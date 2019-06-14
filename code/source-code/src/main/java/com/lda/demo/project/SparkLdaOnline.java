package com.lda.demo.project;

import static org.apache.spark.sql.functions.callUDF;
import static org.apache.spark.sql.functions.col;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.spark.ml.clustering.LDA;
import org.apache.spark.ml.clustering.LocalLDAModel;
import org.apache.spark.ml.feature.CountVectorizer;
import org.apache.spark.ml.feature.CountVectorizerModel;
import org.apache.spark.ml.feature.RegexTokenizer;
import org.apache.spark.ml.feature.StopWordsRemover;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.types.DataTypes;
import org.json.CDL;
import org.json.JSONArray;
import org.json.JSONObject;

import edu.stanford.nlp.simple.Sentence;
import scala.collection.mutable.WrappedArray;

public class SparkLdaOnline {

	private static Logger logger = Logger.getLogger(SparkLdaOnline.class);

	public static void main(String[] args) throws IOException {

		if (args.length < 4) {
			System.err.println(
					"Usage: SparkLdaOnline <datafile_in_hadoop> <statsfilename> <localoutdir> <localparampath>");
			System.exit(1);
		}

		String infile = args[0];
		String statsfilename = args[1];
		String outdir = args[2];
		String parampath = args[3];

		File statsfile = new File(statsfilename);

		/* Instantiate a SparkSession with SqlContext */

		SparkSession spark = SparkSession.builder().appName("SparkLdaOnline").getOrCreate();

		/*
		 * Load the test data(reviews json) from the yelp dataset into spark dataframe
		 */

		logger.info("Online LDA DEMO STARTED");

		Dataset<Row> reviews = spark.read().json(infile);
		Dataset<Row> reviewdf = reviews.select("review_id", "text");
		logger.info(" Review json file is read from HDFS");

		/*
		 * Read the hyper-parameter file from the resources directory into a JSONObject
		 */

		String content = new String(Files.readAllBytes(Paths.get(parampath)));
		JSONObject param = new JSONObject(content);
		JSONArray paramjson = param.getJSONArray("params");
		logger.info(" Parameter json file is read from the local file system");

		/* Preprocessing the data starts here */

		/* Text Tokenization */

		RegexTokenizer regexTokenizer = new RegexTokenizer().setInputCol("text").setOutputCol("words").setPattern("\\W")
				.setMinTokenLength(4);
		Dataset<Row> regexTokenized = regexTokenizer.transform(reviewdf.na().fill("an"));
		logger.info(" Tokenization of data is done");

		/* Removing stopwords */

		StopWordsRemover stopwordsremover = new StopWordsRemover().setInputCol("words")
				.setOutputCol("filteredwithstopwords");
		Dataset<Row> filteredstopwords = stopwordsremover.transform(regexTokenized);
		logger.info(" Removal of stopwords from data is done");

		/* UDF for selecting only nouns */

		UDF1<WrappedArray<String>, String[]> udfnouns = new UDF1<WrappedArray<String>, String[]>() {

			private static final long serialVersionUID = 1L;

			@Override
			public String[] call(WrappedArray<String> words) {
				List<String> tokens = new ArrayList<String>();
				if (!words.isEmpty()) {
					scala.collection.Iterator<String> itr = words.iterator();
					while (itr.hasNext()) {
						String word = itr.next();
						tokens.add(word);
					}
					List<String> tags = new Sentence(tokens).posTags();
					List<String> output = new ArrayList<String>();
					for (int i = 0; i < tags.size(); i++) {
						if (tags.get(i).contains("NN")) {
							output.add(tokens.get(i));
						}
					}

					return output.stream().toArray(String[]::new);
				} else {
					List<String> output = new ArrayList<String>();
					output.add("na");
					return output.stream().toArray(String[]::new);
				}
			}
		};

		/* POStagging of the data and selecting only nouns */

		spark.sqlContext().udf().register("postagger", udfnouns, DataTypes.createArrayType(DataTypes.StringType));

		Dataset<Row> tagged = filteredstopwords.withColumn("postagged",
				callUDF("postagger", col("filteredwithstopwords")));

		logger.info(" POSTagging of data is done ");

		/* Splitting the data into 80 % training and 20% test sets for evaluation */

		double[] weights = new double[] { 0.8, 0.2 };
		List<Dataset<Row>> splits = tagged.randomSplitAsList(weights, 1L);
		Dataset<Row> train = splits.get(0);

		/* Building the vocabulary from the training data */

		CountVectorizerModel countvectormodel = new CountVectorizer().setInputCol("postagged").setOutputCol("features")
				.fit(train);
		String[] vocabulary = countvectormodel.vocabulary();

		logger.info(" Vocabulary is built from the training data");

		/* UDF for converting the term indices of the topic model to term words */

		UDF1<WrappedArray<Integer>, String[]> udfword = new UDF1<WrappedArray<Integer>, String[]>() {

			private static final long serialVersionUID = 1L;

			@Override
			public String[] call(WrappedArray<Integer> indices) throws Exception {
				List<String> words = new ArrayList<String>();
				scala.collection.Iterator<Integer> itr = indices.iterator();
				while (itr.hasNext()) {
					String word = vocabulary[itr.next()];
					words.add(word);
				}
				return words.stream().toArray(String[]::new);
			}
		};

		/* Register the output conversion udf */

		spark.sqlContext().udf().register("termindicestowords", udfword,
				DataTypes.createArrayType(DataTypes.StringType));

		logger.info(" udfword is registered with spark");

		/* Vectorization of training data */

		Dataset<Row> vectorizedTrain = countvectormodel.transform(train);
		Dataset<Row> parsedTrain = vectorizedTrain.select("features");

		logger.info(" Vectorization of training data is done");

		/* Create a JSONArray to collect the statistics for all the runs */

		JSONArray statsjson = new JSONArray();

		/* Loop through the JSONArray and fetch the parameters */

		for (int i = 0; i < paramjson.length(); i++) {

			JSONObject json = paramjson.getJSONObject(i);
			int runid = json.getInt("runid");
			double alpha = json.getDouble("alpha");
			double beta = json.getDouble("beta");
			int k = json.getInt("k");
			int iterations = json.getInt("iterations");
			String dirname = "topicmodel_runid_" + runid;

			logger.info("LDA DEMO STARTED CREATING MODEL FOR RUNID: " + runid);

			logger.info(" Parameters are read from the file for runid :" + runid);

			String modeloutputdir = outdir + "/" + dirname;

			/* Instantiate the EM LDA model with the input params and fixed seed value */

			LDA lda = new LDA().setSeed(80).setMaxIter(iterations).setK(k).setOptimizer("online")
					.setDocConcentration(alpha).setTopicConcentration(beta);

			logger.info(" OnlineLDA model is instantiated for runid :" + runid);

			/* Record the start time for the training and evaluation process */

			long startime = System.currentTimeMillis();

			/* Train the model on the training data */

			LocalLDAModel model = (LocalLDAModel) lda.fit(parsedTrain);

			logger.info(" Online LDA model is trained for runid :" + runid);

			/* Record the Execution time for the training and prediction process */

			long executiontime = startime - System.currentTimeMillis();
			long executioninsecs = TimeUnit.MILLISECONDS.toSeconds(executiontime);

			JSONObject statjson = new JSONObject();

			statjson.put("runid", Integer.toString(runid));
			statjson.put("alpha", Double.toString(alpha));
			statjson.put("beta", Double.toString(beta));
			statjson.put("k", Integer.toString(k));
			statjson.put("optimizer", "Online");
			statjson.put("iterations", Integer.toString(iterations));
			statjson.put("executiontimeinseconds", Long.toString(executioninsecs));
			statjson.put("vocablength", vocabulary.length);
			statsjson.put(statjson);

			/* Interpret the topic model into a word format and save to csv file */

			Dataset<Row> output = model.describeTopics(10).withColumn("termwords",
					callUDF("termindicestowords", col("termIndices")));

			output.repartition(1).write().option("header", "true").json(modeloutputdir);

			logger.info("Output topic model is saved to the directory");

			logger.info("Online LDA DEMO COMPLETED for RUND: " + runid);

		}

		/* Write the statistics json as a CSV file to the local filesystem */

		String csv = CDL.toString(statsjson);
		FileUtils.writeStringToFile(statsfile, csv);

		logger.info("Run statistics file for all the run ids is written to the directory");

		logger.info("Online LDA DEMO COMPLETED");

	}

}

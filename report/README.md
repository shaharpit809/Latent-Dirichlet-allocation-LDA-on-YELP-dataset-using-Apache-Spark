### Contributions:

#### Shilpa:

Designed the end to end process flow from feature extraction, transformation, model computation and evaluation for topic modelling.
Wrote the code using Spark MLlib features.
Explored the Stanford-core NLP package to find the serializable class which can do POS tagging as spark-core NLP is only for Scala objects.
Downloaded the yelp data and created split files of different sizes for loading into HDFS.
Transformed the Spark Topic Model output to term words format by looking up in the dynamic dictionary generated from the data at run time.
Did experiments to find the best hyperparameters (alpha, beta and number of topics) for EM and Online model.
Tuned EM Spark execution based on the execution logs and resource allocation strategy.
Explained Spark execution Model, High level design for the entire NLP pipeline and performance tuning of Spark EM execution in the report.
Wrote README files

#### Karen: 

Did theoretical research of LDA to understand the algorithms, read various papers and analyzed source code of the Spark MLlib LDA library. 
Shared the knowledge with the team to inform the implementation of the code
Explored methods of the Spark MLlib LDA library, testing them with PubMed data to understand the input and output.
Carried out experiments to show the convergence of the algorithms
Carried out experiments to calculate the execution time as a function of the number of iterations and  to calculate the execution time for model measurements (log perplexity and log likelihood).
Created graphs for those experiments and described them in the report. 
Used the theoretical background gained through research to explain the experimental results
Wrote the introduction, description of the algorithms and conclusions in the report. 
Prepared the presentation (content and formatting) 
Wrote README files

#### Arpit:

Understood the LDA algorithm and also how it is implemented in Spark MLlib
Researched about various NLP methods that can help to better pre-process data
Found out various NLP libraries that were compatible with Spark to get a proper end to end flow in Spark itself
Pre-processed Yelp dataset to get better performance
Helped bridge the gap in the team by understanding the technical nuances of the algorithm from Karen and explaining it to Shilpa and similarly understanding the technical nuances of Spark from Shilpa and explaining it to Karen
Carried out several experiments on various values of K to check how the algorithm converges on different sizes of data
Contributed in presentation by providing execution graphs
Contributed in report by providing graphs and helping out with the documentation
Wrote README files

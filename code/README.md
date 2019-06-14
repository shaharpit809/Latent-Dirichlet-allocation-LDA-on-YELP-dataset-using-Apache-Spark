

### Installation steps:

    1.Make directory structure like this in your node: (source_code,lda_output,lda_output_online)
     [user_name@j-login1 lda_project_final_demo]$ ls -lrt
      drwxr-xr-x 4 user_name users 4096 Apr  3 11:17 source_code
      drwxr-xr-x 7 user_name users 4096 Apr  3 11:42 lda_output
      drwxr-xr-x 7 user_name users 4096 Apr  3 11:40 lda_output_online


    2. Copy the src and pom.xml file into source_code, go to the location and run the command mvn package. Do not copy the target folder      from source_folder. It contains an uber jar. This jar you must generate in your folder using mvn package. It will install all 
      the libraries and combine them into an uber jar which will be uploaded in spark jvm when the job starts.

    3. Go to the lda_output directory and create the following directories for all file sizes.The output 
     topic models will be written to these directories in the local file system.
                  
	[user_name@j-login1 lda_output]$ ls -lrt
         drwxr-xr-x 2 user_name users 4096 Apr  3 11:42 run_1G
         drwxr-xr-x 2 user_name users 4096 Apr  3 11:42 run_2G
         drwxr-xr-x 2 user_name users 4096 Apr  3 11:42 run_3G
         drwxr-xr-x 2 user_name users 4096 Apr  3 11:42 run_4G
         drwxr-xr-x 2 user_name users 4096 Apr  3 11:42 run_5G
				  
				  
     4. Create a file hyper-params.json into your lda_output folder and add the below json structure for EM in lda_output folder and    online in the lda_output_online folder. This is for providing the hyper-parameters through a config file externally for testing and getting the best values. 

     For the final run, we are just putting the optimal value of these parameters.

    EM:
                     {
	                "params": [
		      {
			"runid": "1",
			"alpha": "3.0",
			"beta": "1.1",
			"k": "20",
			"iterations": "100
		      }
		]
                       }


    Online:

                        {
	             "params": [
		    {
			"runid": "1",
			"alpha": "0.1",
			"beta": "0.1",
			"k": "10",
			"iterations": "100"
		}
		]
                         }



    5. validate the json in jsonlint.com


    6. Run the following command to start the spark job: Change the file locations accordingly.
                       
  EM yarn command to run the job
         
 spark-submit --deploy-mode client --class com.lda.demo.project.SparkLdaEM                                               /N/u/user_name/lda_project_final_demo/source_code/target/spark-lda-demo-0.0.1-SNAPSHOT-jar-with-dependencies.jar hdfs:///user/user_name/file_splits/review_1G.json /N/u/user_name/lda_project_final_demo/lda_output/statistics_csv               file:///N/u/user_name/lda_project_final_demo/lda_output/run_1G                                                              /N/u/user_name/lda_project_final_demo/lda_output/hyper-params.json
		 
		 
  Online yarn command to run the job

 spark-submit --deploy-mode client --class com.lda.demo.project.SparkLdaOnline   /N/u/user_name/lda_project_final_demo/source_code/target/spark-lda-demo-0.0.1-SNAPSHOT-jar-with-dependencies.jar hdfs:///user/user_name/file_splits/review_1G.json /N/u/user_name/lda_project_final_demo/lda_output_online/statistics_csv file:///N/u/user_name/lda_project_final_demo/lda_output_online/run_1G /N/u/user_name/lda_project_final_demo/lda_output_online/hyper-params.json


Parameter description:

    --class com.lda.demo.project.SparkLdaEM    Main class in jar for EMLda
     /N/u/user_name/lda_project_final_demo/source_code/target/spark-lda-demo-0.0.1-SNAPSHOT-jar-with-dependencies.jar    Classpath
     hdfs:///user/user_name/lda/yelp-data/review_1G.json    hdfs file location where the datafiles are located
     /N/u/user_name/lda_project_final_demo/lda_output/statistics_csv    Output stats file to be created
     file:////N/u/user_name/lda_project_final_demo/lda_output/run_1G   Output directory to store the topic model for each run id for each  file
     /N/u/user_name/lda_project_final_demo/lda_output/hyper-params.json    Input parameter file containing hyper-parameters value for the model.


    7. After every run, please rename the statistics_csv to statistics_csv_<file_size> to avoid  getting it overwritten by next run.

    8. All the above was regarding the model tuning: For the spark tuning, we must make changes in the following file.The below  configuration is the most optimal one for EM.

                         

                           spark.master                     yarn
                           spark.eventLog.enabled           true
                           spark.eventLog.dir               /scratch_hdd/user_name/spark/history
                           spark.history.fs.logDirectory    /scratch_hdd/user_name/spark/history
                           spark.serializer                 org.apache.spark.serializer.KryoSerializer
                           spark.driver.memory              28g
                           spark.executor.memory            14g
                           spark.executor.memoryOverhead    1g
                           spark.executor.instances         12
                           spark.executor.cores             3

    The below configuration is the most optimal one for online. We are creating more thin executors for online.
                          
                           spark.master                      yarn
                           spark.eventLog.enabled            true
                           spark.eventLog.dir                /scratch_hdd/user_name/spark/history
                           spark.history.fs.logDirectory     /scratch_hdd/user_name/spark/history
                           spark.serializer                  org.apache.spark.serializer.KryoSerializer
                           spark.driver.memory               50g
                           spark.executor.memory             5g
                           Spark.executor.memoryOverhea d    1g
                           spark.executor.instances          30
                           spark.executor.cores              1


		 
		 


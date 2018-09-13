package exercise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.spark.SparkConf;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.*;
import org.apache.spark.streaming.kafka09.ConsumerStrategies;
import org.apache.spark.streaming.kafka09.KafkaUtils;
import org.apache.spark.streaming.kafka09.LocationStrategies;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.mapr.db.MapRDB;

public class SparkConsumer {

	// Set the stream and topic to read from.
    public static String topic = "/user/user01/pump:alert";
    public static String tableName = "/tmp/tables/streamsdemo";
       
	public static void main(String[] args) {
		
		Map<String, Object> kafkaParams = new HashMap<>();
		kafkaParams.put("auto.offset.reset", "earliest");
		
		kafkaParams.put("key.deserializer",
	               "org.apache.kafka.common.serialization.StringDeserializer");
		kafkaParams.put("value.deserializer",
	               "org.apache.kafka.common.serialization.StringDeserializer");
		
		List<String> topics = new ArrayList<String>();
		topics.add(topic);
		
		SparkConf sparkConf = new SparkConf()
		        .setAppName("MapR Streaming App")
				//.set("spark.streaming.kafka.maxRetries", String.valueOf(10))
				.set("spark.streaming.kafka.consumer.poll.ms", String.valueOf(1000));
		
		JavaStreamingContext streamingContext = 
				new JavaStreamingContext(sparkConf, Durations.seconds(2));
		
		JavaInputDStream<ConsumerRecord<String, String>> stream =
			KafkaUtils.createDirectStream(
				streamingContext,
				LocationStrategies.PreferConsistent(),
				ConsumerStrategies.<String, String>Subscribe(topics, kafkaParams)
			);

		 JavaDStream<String> values = stream.map(x -> x.value());
		
		 // To print first 10 values of the Dstream
		 values.print();


		// Check if the Maprdb table exists 	
		if(!MapRDB.tableExists(tableName)) 
			MapRDB.createTable(tableName);
		else {
			MapRDB.deleteTable(tableName);
			MapRDB.createTable(tableName);
		}

		// Insert into Maprdb json table
		values.foreachRDD(rdd -> rdd.collect()
				 						.forEach(val -> 
				 							MapRDB.getTable(tableName)
													.insert(MapRDB.newDocument()
															.set("_id", val.split(" ")[1])
															.set("msg_num", val)
															)));
		
		streamingContext.start();
		
		try {
			streamingContext.awaitTermination();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			System.out.println("Error: " + e.getMessage());
		}	
	}	
}

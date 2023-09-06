#!/bin/bash

if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <N> <M>"
  exit 1
fi

N=$1  # Number of calls
M=$2  # Starting index

# Define the base JSON data
base_json='{"name":"s3sink","config":{"connector.class":"io.lenses.connect.aws.s3.S3SinkConnector","tasks.max":"1","topics":"your-topic","s3.bucket.name":"your-bucket-name","s3.region":"your-s3-region","format.class":"io.lenses.connect.aws.s3.format.parquet.ParquetFormat","flush.size":"1000","name":"s3-sink"}}'

# Loop to generate N calls with distinctive names starting from M
for ((i=0; i<$N; i++)); do
  # Calculate the current index by adding M to the loop variable
  current_index=$((M + i))

  # Create a JSON string with a unique index for the name field
  json_data=$(echo "$base_json" | jq ".name=\"s3sink-$current_index\"")

  # Send the POST request with the updated JSON data
  curl -X POST -H "Content-Type: application/json" -d "$json_data" http://localhost:18083/connectors

  # Sleep for a short duration to avoid overloading the server (optional)
  #sleep 0.01
done

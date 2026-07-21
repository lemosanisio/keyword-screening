#!/bin/sh
set -eu

awslocal sqs create-queue --queue-name pld-transaction-signals-dlq >/dev/null
DLQ_URL="$(awslocal sqs get-queue-url --queue-name pld-transaction-signals-dlq --query QueueUrl --output text)"
DLQ_ARN="$(awslocal sqs get-queue-attributes --queue-url "$DLQ_URL" --attribute-names QueueArn --query Attributes.QueueArn --output text)"

awslocal sqs create-queue \
  --queue-name pld-transaction-signals \
  --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$DLQ_ARN\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}" \
  >/dev/null

#!/usr/bin/env sh

#aws s3 cp pipeline-template.yml s3://orestis-test-2/
sam build
sam deploy
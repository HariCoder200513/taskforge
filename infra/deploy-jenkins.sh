#!/usr/bin/env bash
# Provision (or update) the Jenkins EC2 instance via CloudFormation.
#
# Prerequisites:
#   - Valid AWS credentials (aws sts get-caller-identity must succeed)
#   - An existing EC2 key pair (set KEY_NAME below or export it)
#
# Usage:
#   KEY_NAME=my-keypair ./infra/deploy-jenkins.sh
#   KEY_NAME=my-keypair SSH_CIDR=203.0.113.4/32 AWS_REGION=us-east-2 ./infra/deploy-jenkins.sh

set -euo pipefail

STACK_NAME="${STACK_NAME:-taskforge-jenkins}"
AWS_REGION="${AWS_REGION:-us-east-2}"
KEY_NAME="${KEY_NAME:?Set KEY_NAME to an existing EC2 key pair}"
INSTANCE_TYPE="${INSTANCE_TYPE:-t3.medium}"
SSH_CIDR="${SSH_CIDR:-0.0.0.0/0}"
JENKINS_CIDR="${JENKINS_CIDR:-0.0.0.0/0}"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

aws cloudformation deploy \
  --region "$AWS_REGION" \
  --stack-name "$STACK_NAME" \
  --template-file "$HERE/jenkins-ec2.yaml" \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides \
      KeyName="$KEY_NAME" \
      InstanceType="$INSTANCE_TYPE" \
      SshAllowedCidr="$SSH_CIDR" \
      JenkinsAllowedCidr="$JENKINS_CIDR"

echo "---- Outputs ----"
aws cloudformation describe-stacks \
  --region "$AWS_REGION" \
  --stack-name "$STACK_NAME" \
  --query 'Stacks[0].Outputs' \
  --output table

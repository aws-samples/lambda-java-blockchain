#!/bin/bash

if [ -z "$ADMINUSER" ] || [ -z "$ADMINPWD" ] || [ -z "$LAMBDAUSERPWD" ] || [ -z "$REGION" ] || [ -z "$NETWORKNAME" ]; then
  echo "Please ensure environment variables ADMINUSER, ADMINPWD, LAMBDAUSERPWD, REGION and NETWORKNAME are set!"
  exit 1
fi

echo "_______________________________________"
echo "1. Exporting environment variables"
echo "_______________________________________"

export NETWORKID=$(aws managedblockchain list-networks --name $NETWORKNAME --region $REGION --query 'Networks[0].Id' --output text)
export NETWORKVERSION=1.2
export MEMBERID=$(aws managedblockchain list-members --region $REGION --network-id $NETWORKID --query 'Members[?IsOwned].Id' --output text)
export MEMBERNAME=$(aws managedblockchain list-members --region $REGION --network-id $NETWORKID --query 'Members[?IsOwned].Name' --output text)
export PEERID=$(aws managedblockchain list-nodes --region $REGION --network-id $NETWORKID --member-id $MEMBERID --query 'Nodes[0].Id' --output text)

VpcEndpointServiceName=$(aws managedblockchain get-network --region $REGION --network-id $NETWORKID --query 'Network.VpcEndpointServiceName' --output text)
OrdererEndpoint=$(aws managedblockchain get-network --region $REGION --network-id $NETWORKID --query 'Network.FrameworkAttributes.Fabric.OrderingServiceEndpoint' --output text)
CaEndpoint=$(aws managedblockchain get-member --region $REGION --network-id $NETWORKID --member-id $MEMBERID --query 'Member.FrameworkAttributes.Fabric.CaEndpoint' --output text)
PeerEndpoint=$(aws managedblockchain get-node --region $REGION --network-id $NETWORKID --member-id $MEMBERID --node-id $PEERID --query 'Node.FrameworkAttributes.Fabric.PeerEndpoint' --output text)
PeerEventEndpoint=$(aws managedblockchain get-node --region $REGION --network-id $NETWORKID --member-id $MEMBERID --node-id $PEERID --query 'Node.FrameworkAttributes.Fabric.PeerEventEndpoint' --output text)

echo "export REGION=$REGION"
echo "export NETWORKNAME=$NETWORKNAME"
echo "export NETWORKID=$NETWORKID"
echo "export MEMBERNAME=$MEMBERNAME"
echo "export MEMBERID=$MEMBERID"
echo "export PEERID=$PEERID"
echo "export ADMINUSER=$ADMINUSER"
echo -e "export ADMINPWD='${ADMINPWD}'"
echo "export LAMBDAUSER=lambdaUser"
echo -e "export LAMBDAUSERPWD='${LAMBDAUSERPWD}'"
echo "export CAENDPOINT=$CaEndpoint"
echo "export ORDERERENDPOINT=$OrdererEndpoint"
echo "export PEERENDPOINT=$PeerEndpoint"
echo "export PEEREVENTENDPOINT=$PeerEventEndpoint"
echo "export AMBVpcEndpointServiceName=$VpcEndpointServiceName"

echo "_______________________________________"
echo "2. Deploying the stack with 'sam deploy'"
echo "_______________________________________"

bucketName="lambda-java-blockchain-sam-bucket-"$(aws sts get-caller-identity --query "Account" --output text)
echo "Creating s3 bucket to host lambda sources: $bucketName"
aws s3 mb s3://$bucketName --region $REGION

sam deploy --stack-name lambda-java-blockchain --region $REGION --s3-bucket $bucketName \
  --capabilities CAPABILITY_IAM --parameter-overrides \
  AMBREGION=$REGION NETWORKID=$NETWORKID \
  MEMBERNAME=$MEMBERNAME MEMBERID=$MEMBERID PEERID=$PEERID \
  ADMINUSER=$ADMINUSER ADMINPWD=${ADMINPWD} \
  LAMBDAUSER=lambdaUser LAMBDAUSERPWD=${LAMBDAUSERPWD} \
  CAENDPOINT=$CaEndpoint \
  ORDERERENDPOINT=$OrdererEndpoint \
  PEERENDPOINT=$PeerEndpoint \
  CHANNELNAME=mychannel CHAINCODENAME=mycc \
  AMBVpcEndpointServiceName=$VpcEndpointServiceName

echo "Lambda source code is stored in the S3 bucket - "$bucketName""

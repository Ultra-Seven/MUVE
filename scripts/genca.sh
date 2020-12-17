#!/bin/bash



keytool -delete -noprompt -alias vozca  -keystore vozca.jks -storepass password

# Create a self signed key pair root CA certificate.
keytool -genkeypair -v \
  -alias vozca \
  -dname "CN=exampleCA, OU=Example Org, O=Example Company, L=San Francisco, ST=California, C=US" \
  -keystore vozca.jks \
  -keypass password \
  -storepass password \
  -keyalg RSA \
  -keysize 4096 \
  -ext KeyUsage="keyCertSign" \
  -ext BasicConstraints:"critical=ca:true" \
  -validity 300

# Export the exampleCA public certificate so that it can be used in trust stores..
keytool -export -v \
  -alias vozca \
  -file vozca.crt \
  -keypass password \
  -storepass password \
  -keystore vozca.jks \
  -rfc

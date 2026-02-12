#!/bin/bash

# Generate SAML keystore for testing
# DO NOT use in production without proper certificates

set -e

KEYSTORE_FILE="src/main/resources/saml/keystore.jks"
KEY_ALIAS="saml"
KEY_PASSWORD="changeit"
DN="CN=buerokratt.ee,OU=IT,O=Bürokratt,L=Tallinn,C=EE"
VALIDITY=365  # days

echo "=== Generating SAML Keystore ==="

# Create directory
mkdir -p "$(dirname "$KEYSTORE_FILE")"

# Generate keystore
keytool -genkeypair \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity "$VALIDITY" \
  -dname "$DN" \
  -keystore "$KEYSTORE_FILE" \
  -storepass "$KEY_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -noprompt

# Export certificate
keytool -exportcert \
  -alias "$KEY_ALIAS" \
  -file src/main/resources/saml/cert.pem \
  -keystore "$KEYSTORE_FILE" \
  -storepass "$KEY_PASSWORD" \
  -noprompt

echo "✅ Keystore generated: $KEYSTORE_FILE"
echo "✅ Certificate exported: src/main/resources/saml/cert.pem"
echo ""
echo "⚠️  WARNING: This is a self-signed certificate for testing only!"
echo "⚠️  In production, use certificates signed by your organization's CA"
echo ""
echo "Keystore password: $KEY_PASSWORD"
echo "Key password: $KEY_PASSWORD"

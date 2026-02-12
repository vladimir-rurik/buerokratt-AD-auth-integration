#!/bin/bash

# Test AD Authentication Flow

set -e

BASE_URL="http://localhost:8085"
echo "=== AD Authentication Test ==="
echo ""

# 1. Test health endpoint
echo "1. Testing health endpoint..."
curl -s "$BASE_URL/actuator/health" | jq .
echo ""

# 2. Initiate authentication
echo "2. Initiating AD authentication..."
AUTH_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/ad/login")
REDIRECT_URL=$(echo "$AUTH_RESPONSE" | jq -r .redirectUrl)
echo "Redirect URL: $REDIRECT_URL"
echo ""

# 3. Test role mapping
echo "3. Testing role mapping..."
ROLE_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/ad/map-roles" \
  -H "Content-Type: application/json" \
  -d '{
    "adGroups": [
      "CN=Buerokratt-Admins,OU=Groups,DC=domain,DC=com",
      "CN=All-Users,OU=Groups,DC=domain,DC=com"
    ]
  }')
echo "Role mapping result:"
echo "$ROLE_RESPONSE" | jq .
echo ""

echo "=== Test Complete ==="

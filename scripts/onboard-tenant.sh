#!/usr/bin/env sh
set -eu

: "${CONTRACT_PDF:?Set CONTRACT_PDF to the approved contract PDF}"
: "${SIGNING_CERT_PEM:?Set SIGNING_CERT_PEM to the certificate PEM file}"
: "${SIGNING_KEY_PEM:?Set SIGNING_KEY_PEM to the private-key PEM file}"

curl --fail-with-body --request POST http://localhost:8080/admin/tenants \
  --form tenantId=academy-42 \
  --form 'legalName=Northwind Learning' \
  --form "contract=@${CONTRACT_PDF};type=application/pdf" \
  --form "certificate=@${SIGNING_CERT_PEM};type=application/x-pem-file" \
  --form "privateKey=@${SIGNING_KEY_PEM};type=application/x-pem-file"

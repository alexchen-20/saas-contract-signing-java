package dev.learning.contracts.domain;

import com.fasterxml.jackson.databind.JsonNode;

public interface ContractSigner {
    JsonNode sign(byte[] pdf, String certPem, String keyPem, String idempotencyKey);
}

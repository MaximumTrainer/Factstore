package api

import (
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// TypedAttestRequest is the body for POST /api/v1/trails/{trailId}/attestations.
type TypedAttestRequest struct {
	Type            string `json:"type"`
	Status          string `json:"status"`
	Name            string `json:"name,omitempty"`
	Details         string `json:"details,omitempty"`
	AttestationData string `json:"attestationData,omitempty"`
	EvidenceUrl     string `json:"evidenceUrl,omitempty"`
	GitCommitSha    string `json:"gitCommitSha,omitempty"`
	GitBranch       string `json:"gitBranch,omitempty"`
}

// RecordTypedAttestation records an attestation on a trail via the v1 API.
func RecordTypedAttestation(c *client.Client, trailID string, req TypedAttestRequest) (*AttestationResponse, error) {
	body, status, err := c.Post("/api/v1/trails/"+trailID+"/attestations", req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusCreated && status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var result AttestationResponse
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &result, nil
}

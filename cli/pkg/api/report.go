package api

import (
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// LiveArtifactEntry represents a currently deployed artifact in an environment.
type LiveArtifactEntry struct {
	ArtifactName   string `json:"artifactName"`
	ArtifactTag    string `json:"artifactTag"`
	ArtifactSha256 string `json:"artifactSha256"`
	EnvironmentID  string `json:"environmentId"`
	EnvironmentName string `json:"environmentName"`
	OrgSlug        string `json:"orgSlug,omitempty"`
}

// GetLiveArtifacts returns the current live artifacts across all environments.
func GetLiveArtifacts(c *client.Client) ([]LiveArtifactEntry, error) {
	body, status, err := c.Get("/api/v1/environments/live-artifacts")
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var artifacts []LiveArtifactEntry
	if err := json.Unmarshal(body, &artifacts); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return artifacts, nil
}

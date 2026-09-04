package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// TrailCascadeCounts is what removing a trail took with it.
type TrailCascadeCounts struct {
	Attestations          int `json:"attestations"`
	Artifacts             int `json:"artifacts"`
	EvidenceFiles         int `json:"evidenceFiles"`
	Approvals             int `json:"approvals"`
	CoverageReports       int `json:"coverageReports"`
	SecurityScans         int `json:"securityScans"`
	ComplianceAssessments int `json:"complianceAssessments"`
	JiraTickets           int `json:"jiraTickets"`
	Total                 int `json:"total"`
}

// TrailDeletionResponse mirrors the backend response for a hard delete.
type TrailDeletionResponse struct {
	TrailID string             `json:"trailId"`
	Cascade TrailCascadeCounts `json:"cascade"`
}

// TrailCleanupRequest selects trails for bulk cleanup. At least one selector is required.
type TrailCleanupRequest struct {
	FlowID   string `json:"flowId,omitempty"`
	TagKey   string `json:"tagKey,omitempty"`
	TagValue string `json:"tagValue,omitempty"`
	// OlderThan is an RFC 3339 instant; trails created strictly before it are selected.
	OlderThan string `json:"olderThan,omitempty"`
	// Mode is ARCHIVE (default, reversible) or DELETE.
	Mode   string `json:"mode,omitempty"`
	DryRun bool   `json:"dryRun"`
}

// TrailCleanupResponse reports what was, or would be, removed.
type TrailCleanupResponse struct {
	DryRun     bool               `json:"dryRun"`
	Mode       string             `json:"mode"`
	TrailCount int                `json:"trailCount"`
	TrailIDs   []string           `json:"trailIds"`
	Cascade    TrailCascadeCounts `json:"cascade"`
}

// ArchiveTrail soft-deletes a trail, retaining its evidence.
func ArchiveTrail(c *client.Client, trailID string) (*TrailResponse, error) {
	return postTrailState(c, trailID, "archive")
}

// UnarchiveTrail brings an archived trail back into the default listings.
func UnarchiveTrail(c *client.Client, trailID string) (*TrailResponse, error) {
	return postTrailState(c, trailID, "unarchive")
}

func postTrailState(c *client.Client, trailID, action string) (*TrailResponse, error) {
	body, status, err := c.Post("/api/v1/trails/"+url.PathEscape(trailID)+"/"+action, map[string]any{})
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var trail TrailResponse
	if err := json.Unmarshal(body, &trail); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &trail, nil
}

// DeleteTrail permanently removes a trail and the evidence it owns.
func DeleteTrail(c *client.Client, trailID string) (*TrailDeletionResponse, error) {
	body, status, err := c.Delete("/api/v1/trails/" + url.PathEscape(trailID))
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK && status != http.StatusNoContent {
		return nil, client.ParseError(status, body)
	}
	var result TrailDeletionResponse
	if len(body) > 0 {
		if err := json.Unmarshal(body, &result); err != nil {
			return nil, fmt.Errorf("parse response: %w", err)
		}
	}
	return &result, nil
}

// CleanupTrails runs a bulk cleanup. Set DryRun to report without changing anything.
func CleanupTrails(c *client.Client, req TrailCleanupRequest) (*TrailCleanupResponse, error) {
	body, status, err := c.Post("/api/v1/trails/cleanup", req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var result TrailCleanupResponse
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &result, nil
}

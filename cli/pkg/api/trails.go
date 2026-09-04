package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// TrailResponse mirrors the backend TrailResponse DTO.
type TrailResponse struct {
	ID                  string `json:"id"`
	FlowID              string `json:"flowId"`
	GitCommitSha        string `json:"gitCommitSha"`
	GitBranch           string `json:"gitBranch"`
	GitAuthor           string `json:"gitAuthor"`
	GitAuthorEmail      string `json:"gitAuthorEmail"`
	PullRequestID       string `json:"pullRequestId,omitempty"`
	PullRequestReviewer string `json:"pullRequestReviewer,omitempty"`
	DeploymentActor     string `json:"deploymentActor,omitempty"`
	Status              string `json:"status"`
	Name                string `json:"name,omitempty"`
	// ExternalID is the stable release identifier a downstream pipeline can address
	// the trail by, without knowing its UUID.
	ExternalID string `json:"externalId,omitempty"`
	CreatedAt  string `json:"createdAt"`
	UpdatedAt  string `json:"updatedAt"`
}

// TrailSelector addresses a trail within a flow without its UUID. Exactly one field is used,
// in the order ExternalID, Name, GitCommitSha (the most recent run for that commit).
type TrailSelector struct {
	ExternalID   string
	Name         string
	GitCommitSha string
}

// CreateTrailRequest is the body for POST /api/v2/trails.
type CreateTrailRequest struct {
	FlowID              string `json:"flowId"`
	GitCommitSha        string `json:"gitCommitSha"`
	GitBranch           string `json:"gitBranch"`
	GitAuthor           string `json:"gitAuthor"`
	GitAuthorEmail      string `json:"gitAuthorEmail"`
	PullRequestID       string `json:"pullRequestId,omitempty"`
	PullRequestReviewer string `json:"pullRequestReviewer,omitempty"`
	DeploymentActor     string `json:"deploymentActor,omitempty"`
	Name                string `json:"name,omitempty"`
	// ExternalID makes trail creation idempotent per flow: a re-run of the pipeline that
	// owns the release attaches to the same trail instead of forking the evidence.
	ExternalID string `json:"externalId,omitempty"`
}

// ListTrails returns all trails, optionally filtered by flowId (query path).
func ListTrails(c *client.Client, flowID string) ([]TrailResponse, error) {
	path := "/api/v2/trails"
	if flowID != "" {
		q := url.Values{}
		q.Set("flowId", flowID)
		path += "?" + q.Encode()
	}
	body, status, err := c.Get(path)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var trails []TrailResponse
	if err := json.Unmarshal(body, &trails); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return trails, nil
}

// LookupTrail resolves a trail within a flow by release identifier, name, or commit SHA.
func LookupTrail(c *client.Client, flowID string, selector TrailSelector) (*TrailResponse, error) {
	q := url.Values{}
	q.Set("flowId", flowID)
	switch {
	case selector.ExternalID != "":
		q.Set("externalId", selector.ExternalID)
	case selector.Name != "":
		q.Set("name", selector.Name)
	case selector.GitCommitSha != "":
		q.Set("gitCommitSha", selector.GitCommitSha)
	default:
		return nil, fmt.Errorf("one of external-id, name or commit is required")
	}
	body, status, err := c.Get("/api/v1/trails/lookup?" + q.Encode())
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

// ResolveTrailID returns trailID when set, otherwise resolves the trail from flowID and
// selector. Lets a downstream job name the release instead of plumbing a UUID.
func ResolveTrailID(c *client.Client, trailID, flowID string, selector TrailSelector) (string, error) {
	if trailID != "" {
		return trailID, nil
	}
	if selector.ExternalID == "" && selector.Name == "" && selector.GitCommitSha == "" {
		return "", fmt.Errorf("--trail-id, or --flow-id with one of --trail-external-id/--trail-name/--commit, is required")
	}
	if flowID == "" {
		return "", fmt.Errorf("--flow-id is required to resolve a trail by name or release identifier")
	}
	trail, err := LookupTrail(c, flowID, selector)
	if err != nil {
		return "", err
	}
	return trail.ID, nil
}

// GetTrail returns a single trail by ID (query path).
func GetTrail(c *client.Client, id string) (*TrailResponse, error) {
	body, status, err := c.Get("/api/v2/trails/" + id)
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

// CreateTrail creates a new trail (command path).
func CreateTrail(c *client.Client, req CreateTrailRequest) (*CommandResult, error) {
	body, status, err := c.Post("/api/v2/trails", req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusCreated && status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var result CommandResult
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &result, nil
}

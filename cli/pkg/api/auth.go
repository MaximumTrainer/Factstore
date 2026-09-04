package api

import (
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
)

// PrincipalOrganisation is one organisation a user belongs to.
type PrincipalOrganisation struct {
	OrgSlug string `json:"orgSlug"`
	Role    string `json:"role"`
}

// AuthenticatedPrincipal mirrors GET /api/v1/auth/me. It answers for both a user session and
// an API key, so one call tells the CLI whether its credential works and what it may do.
type AuthenticatedPrincipal struct {
	Type          string                  `json:"type"`
	UserID        string                  `json:"userId,omitempty"`
	Email         string                  `json:"email,omitempty"`
	Name          string                  `json:"name,omitempty"`
	OwnerID       string                  `json:"ownerId,omitempty"`
	OrgSlug       string                  `json:"orgSlug,omitempty"`
	Role          string                  `json:"role,omitempty"`
	Permissions   []string                `json:"permissions"`
	Organisations []PrincipalOrganisation `json:"organisations"`
}

// WhoAmI asks the server to identify the configured credential.
func WhoAmI(c *client.Client) (*AuthenticatedPrincipal, error) {
	body, status, err := c.Get("/api/v1/auth/me")
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, client.ParseError(status, body)
	}
	var principal AuthenticatedPrincipal
	if err := json.Unmarshal(body, &principal); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	return &principal, nil
}

package tests

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"

	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
)

// #164: a downstream pipeline must be able to resolve the trail the primary pipeline
// created without any UUID plumbing.
func TestLookupTrailByExternalID(t *testing.T) {
	var query url.Values
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet || r.URL.Path != "/api/v1/trails/lookup" {
			t.Errorf("unexpected: %s %s", r.Method, r.URL.Path)
		}
		query = r.URL.Query()
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(api.TrailResponse{
			ID:         "trail-1",
			FlowID:     "flow-1",
			ExternalID: "release-77",
		})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	trail, err := api.LookupTrail(c, "flow-1", api.TrailSelector{ExternalID: "release-77"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got := query.Get("flowId"); got != "flow-1" {
		t.Errorf("expected flowId=flow-1, got %q", got)
	}
	if got := query.Get("externalId"); got != "release-77" {
		t.Errorf("expected externalId=release-77, got %q", got)
	}
	if trail.ID != "trail-1" || trail.ExternalID != "release-77" {
		t.Errorf("unexpected trail: %+v", trail)
	}
}

func TestLookupTrailSendsOnlyTheSelectorGiven(t *testing.T) {
	var query url.Values
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.Query()
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(api.TrailResponse{ID: "trail-2"})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	if _, err := api.LookupTrail(c, "flow-1", api.TrailSelector{GitCommitSha: "abc123"}); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if _, present := query["externalId"]; present {
		t.Error("externalId must not be sent when it was not supplied")
	}
	if _, present := query["name"]; present {
		t.Error("name must not be sent when it was not supplied")
	}
	if got := query.Get("gitCommitSha"); got != "abc123" {
		t.Errorf("expected gitCommitSha=abc123, got %q", got)
	}
}

func TestLookupTrailRequiresASelector(t *testing.T) {
	c := mustNewClient(t, "http://127.0.0.1:1", "tok")
	if _, err := api.LookupTrail(c, "flow-1", api.TrailSelector{}); err == nil {
		t.Error("expected an error when no selector is supplied")
	}
}

func TestLookupTrailNotFound(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		w.Write([]byte(`{"message":"No trail matching"}`))
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	if _, err := api.LookupTrail(c, "flow-1", api.TrailSelector{ExternalID: "nope"}); err == nil {
		t.Error("expected an error for a 404")
	}
}

// Trail creation carries the release identifier, so a re-run attaches instead of forking.
func TestCreateTrailSendsExternalID(t *testing.T) {
	var raw map[string]any
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewDecoder(r.Body).Decode(&raw)
		w.WriteHeader(http.StatusCreated)
		w.Write([]byte(`{"id":"trail-1","status":"created"}`))
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	result, err := api.CreateTrail(c, api.CreateTrailRequest{
		FlowID:         "flow-1",
		GitCommitSha:   "abc",
		GitBranch:      "main",
		GitAuthor:      "a",
		GitAuthorEmail: "a@example.com",
		ExternalID:     "release-77",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if raw["externalId"] != "release-77" {
		t.Errorf("expected externalId to be sent, got %v", raw["externalId"])
	}
	if result.Status != "created" {
		t.Errorf("unexpected status: %s", result.Status)
	}
}

func TestCreateTrailOmitsExternalIDWhenUnset(t *testing.T) {
	var raw map[string]any
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewDecoder(r.Body).Decode(&raw)
		w.WriteHeader(http.StatusCreated)
		w.Write([]byte(`{"id":"trail-1","status":"created"}`))
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	_, err := api.CreateTrail(c, api.CreateTrailRequest{
		FlowID: "flow-1", GitCommitSha: "abc", GitBranch: "main",
		GitAuthor: "a", GitAuthorEmail: "a@example.com",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if _, present := raw["externalId"]; present {
		t.Error("externalId must be omitted when not supplied")
	}
}

// A re-run reports status "exists" rather than a second trail.
func TestCreateTrailReportsReuse(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"id":"trail-1","status":"exists"}`))
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	result, err := api.CreateTrail(c, api.CreateTrailRequest{
		FlowID: "flow-1", GitCommitSha: "abc", GitBranch: "main",
		GitAuthor: "a", GitAuthorEmail: "a@example.com", ExternalID: "release-77",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Status != "exists" {
		t.Errorf("expected status exists, got %s", result.Status)
	}
}

// ResolveTrailID lets a downstream job name the release instead of plumbing a UUID.
func TestResolveTrailIDPrefersAnExplicitID(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Error("no lookup should be made when a trail id is supplied")
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	id, err := api.ResolveTrailID(c, "trail-explicit", "flow-1", api.TrailSelector{ExternalID: "release-77"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if id != "trail-explicit" {
		t.Errorf("expected trail-explicit, got %q", id)
	}
}

func TestResolveTrailIDLooksUpByExternalID(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/trails/lookup" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(api.TrailResponse{ID: "trail-resolved"})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	id, err := api.ResolveTrailID(c, "", "flow-1", api.TrailSelector{ExternalID: "release-77"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if id != "trail-resolved" {
		t.Errorf("expected trail-resolved, got %q", id)
	}
}

func TestResolveTrailIDRequiresAFlowToResolveAgainst(t *testing.T) {
	c := mustNewClient(t, "http://127.0.0.1:1", "tok")
	if _, err := api.ResolveTrailID(c, "", "", api.TrailSelector{ExternalID: "release-77"}); err == nil {
		t.Error("expected an error when no flow id is supplied")
	}
}

func TestResolveTrailIDRequiresSomething(t *testing.T) {
	c := mustNewClient(t, "http://127.0.0.1:1", "tok")
	if _, err := api.ResolveTrailID(c, "", "flow-1", api.TrailSelector{}); err == nil {
		t.Error("expected an error when neither a trail id nor a selector is supplied")
	}
}

package tests

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
)

// #163: an assertion must be scopeable to the pipeline execution being judged.
func TestAssertSendsTrailIDAndReadsItBack(t *testing.T) {
	var received api.AssertRequest
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/assert" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		json.NewDecoder(r.Body).Decode(&received)
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(api.AssertResponse{
			Sha256Digest: received.Sha256Digest,
			FlowID:       received.FlowID,
			TrailID:      received.TrailID,
			Status:       "COMPLIANT",
		})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	result, err := api.Assert(c, api.AssertRequest{
		Sha256Digest: "sha256:abc",
		FlowID:       "flow-1",
		TrailID:      "trail-9",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if received.TrailID != "trail-9" {
		t.Errorf("expected trailId to be sent, got %q", received.TrailID)
	}
	if result.TrailID != "trail-9" {
		t.Errorf("expected deciding trail in response, got %q", result.TrailID)
	}
}

func TestAssertOmitsTrailIDWhenUnset(t *testing.T) {
	var raw map[string]any
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewDecoder(r.Body).Decode(&raw)
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(api.AssertResponse{Status: "COMPLIANT"})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	if _, err := api.Assert(c, api.AssertRequest{Sha256Digest: "sha256:abc", FlowID: "flow-1"}); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if _, present := raw["trailId"]; present {
		t.Error("trailId must be omitted when not supplied")
	}
}

// #163: a pipeline asserting its own run needs no digest at all.
func TestAssertTrailPostsToTrailEndpoint(t *testing.T) {
	var raw map[string]any
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/v1/trails/trail-9/assert" {
			t.Errorf("unexpected: %s %s", r.Method, r.URL.Path)
		}
		json.NewDecoder(r.Body).Decode(&raw)
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(api.AssertResponse{
			FlowID:  "flow-1",
			TrailID: "trail-9",
			Status:  "NON_COMPLIANT",
			MissingAttestationTypes: []string{"snyk"},
		})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	result, err := api.AssertTrail(c, "trail-9", api.TrailAssertRequest{})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if _, present := raw["sha256Digest"]; present {
		t.Error("sha256Digest must be omitted when not supplied")
	}
	if result.TrailID != "trail-9" || result.Status != "NON_COMPLIANT" {
		t.Errorf("unexpected result: %+v", result)
	}
}

func TestAssertTrailPassesFlowAndDigestWhenGiven(t *testing.T) {
	var raw map[string]any
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewDecoder(r.Body).Decode(&raw)
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(api.AssertResponse{Status: "COMPLIANT"})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	_, err := api.AssertTrail(c, "trail-9", api.TrailAssertRequest{
		FlowID:       "flow-2",
		Sha256Digest: "sha256:def",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if raw["flowId"] != "flow-2" {
		t.Errorf("expected flowId to be sent, got %v", raw["flowId"])
	}
	if raw["sha256Digest"] != "sha256:def" {
		t.Errorf("expected sha256Digest to be sent, got %v", raw["sha256Digest"])
	}
}

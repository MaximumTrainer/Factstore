package tests

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"

	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
)

// #161: scripted teardown of test data.
func TestArchiveTrail(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/v1/trails/trail-1/archive" {
			t.Errorf("unexpected: %s %s", r.Method, r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(api.TrailResponse{ID: "trail-1", ArchivedAt: "2026-02-01T10:00:00Z"})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	trail, err := api.ArchiveTrail(c, "trail-1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if trail.ArchivedAt == "" {
		t.Error("expected the archived timestamp to come back")
	}
}

func TestUnarchiveTrail(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/trails/trail-1/unarchive" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(api.TrailResponse{ID: "trail-1"})
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	if _, err := api.UnarchiveTrail(c, "trail-1"); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
}

func TestDeleteTrailReturnsCascadeCounts(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodDelete || r.URL.Path != "/api/v1/trails/trail-1" {
			t.Errorf("unexpected: %s %s", r.Method, r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"trailId":"trail-1","cascade":{"attestations":3,"artifacts":1,"evidenceFiles":2,"total":6}}`))
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	result, err := api.DeleteTrail(c, "trail-1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Cascade.Attestations != 3 || result.Cascade.Total != 6 {
		t.Errorf("unexpected cascade: %+v", result.Cascade)
	}
}

func TestCleanupTrailsDefaultsToADryRun(t *testing.T) {
	var body api.TrailCleanupRequest
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/trails/cleanup" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		json.NewDecoder(r.Body).Decode(&body)
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"dryRun":true,"mode":"ARCHIVE","trailCount":2,"trailIds":["a","b"],"cascade":{"total":4}}`))
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	result, err := api.CleanupTrails(c, api.TrailCleanupRequest{FlowID: "flow-1", DryRun: true})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !body.DryRun {
		t.Error("expected the dry-run flag to be sent")
	}
	if !result.DryRun || result.TrailCount != 2 {
		t.Errorf("unexpected result: %+v", result)
	}
}

func TestCleanupTrailsSendsOnlyTheSelectorsGiven(t *testing.T) {
	var raw map[string]any
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewDecoder(r.Body).Decode(&raw)
		w.Write([]byte(`{"dryRun":true,"mode":"DELETE","trailCount":0,"trailIds":[],"cascade":{}}`))
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	_, err := api.CleanupTrails(c, api.TrailCleanupRequest{
		TagKey: "env", TagValue: "demo", Mode: "DELETE", DryRun: true,
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if _, present := raw["flowId"]; present {
		t.Error("flowId must be omitted when not supplied")
	}
	if _, present := raw["olderThan"]; present {
		t.Error("olderThan must be omitted when not supplied")
	}
	if raw["tagKey"] != "env" || raw["mode"] != "DELETE" {
		t.Errorf("unexpected body: %v", raw)
	}
}

func TestDeleteFlowRefusalIsSurfaced(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusConflict)
		w.Write([]byte(`{"message":"Flow still has 3 trail(s)"}`))
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	err := api.DeleteFlow(c, "flow-1", false)
	if err == nil {
		t.Fatal("expected the refusal to surface as an error")
	}
}

func TestDeleteFlowForcePassesTheFlag(t *testing.T) {
	var query url.Values
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.Query()
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	if err := api.DeleteFlow(c, "flow-1", true); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if query.Get("force") != "true" {
		t.Errorf("expected force=true, got %q", query.Get("force"))
	}
}

func TestDeleteFlowOmitsForceWhenNotAsked(t *testing.T) {
	var query url.Values
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.Query()
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	if err := api.DeleteFlow(c, "flow-1", false); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if _, present := query["force"]; present {
		t.Error("force must not be sent unless requested")
	}
}

package provider_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/MaximumTrainer/factstore-terraform-provider/internal/provider"
)

// TestNewClient_TrimsTrailingSlash verifies that a trailing slash in base_url is normalized.
func TestNewClient_TrimsTrailingSlash(t *testing.T) {
	var capturedPath string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedPath = r.URL.Path
		w.WriteHeader(http.StatusNotFound)
	}))
	defer srv.Close()

	client := provider.NewClient(srv.URL+"/", "")
	_, _ = client.GetFlow(t.Context(), "some-id")
	if strings.HasPrefix(capturedPath, "//") {
		t.Errorf("expected no double slash, got path: %s", capturedPath)
	}
}

// TestClientUpdateFlow_EmptyAttestationTypes verifies that an empty attestation types list
// is serialized as [] rather than being omitted (omitempty regression).
func TestClientUpdateFlow_EmptyAttestationTypes(t *testing.T) {
	var receivedBody map[string]interface{}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_ = json.NewDecoder(r.Body).Decode(&receivedBody)
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"id":"f1","name":"flow","description":"","requiredAttestationTypes":[],"createdAt":"2024-01-01T00:00:00Z","updatedAt":"2024-01-01T00:00:00Z"}`))
	}))
	defer srv.Close()

	name := "flow"
	desc := ""
	client := provider.NewClient(srv.URL, "")
	_, err := client.UpdateFlow(t.Context(), "f1", provider.FlowUpdateRequest{
		Name:                    &name,
		Description:             &desc,
		RequiredAttestationTypes: []string{},
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	val, ok := receivedBody["requiredAttestationTypes"]
	if !ok {
		t.Fatal("requiredAttestationTypes must be present in update payload (not omitted)")
	}
	if val == nil {
		t.Fatal("requiredAttestationTypes must be [] not null in update payload")
	}
}

// TestClientUpdatePolicy_EmptyAttestationTypes verifies the same for PolicyUpdateRequest.
func TestClientUpdatePolicy_EmptyAttestationTypes(t *testing.T) {
	var receivedBody map[string]interface{}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_ = json.NewDecoder(r.Body).Decode(&receivedBody)
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"id":"p1","name":"pol","enforceProvenance":false,"enforceTrailCompliance":false,"requiredAttestationTypes":[],"createdAt":"2024-01-01T00:00:00Z","updatedAt":"2024-01-01T00:00:00Z"}`))
	}))
	defer srv.Close()

	name := "pol"
	client := provider.NewClient(srv.URL, "")
	_, err := client.UpdatePolicy(t.Context(), "p1", provider.PolicyUpdateRequest{
		Name:                    &name,
		RequiredAttestationTypes: []string{},
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	val, ok := receivedBody["requiredAttestationTypes"]
	if !ok {
		t.Fatal("requiredAttestationTypes must be present in update payload (not omitted)")
	}
	if val == nil {
		t.Fatal("requiredAttestationTypes must be [] not null in update payload")
	}
}

// TestClientDeletePolicy_NotFound verifies that a 404 on delete is treated as success.
func TestClientDeletePolicy_NotFound(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	defer srv.Close()

	client := provider.NewClient(srv.URL, "")
	if err := client.DeletePolicy(t.Context(), "missing"); err != nil {
		t.Fatalf("unexpected error on 404 delete: %v", err)
	}
}

// TestClientCreatePolicyAttachment_Success verifies policy attachment creation.
func TestClientCreatePolicyAttachment_Success(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		_, _ = w.Write([]byte(`{"id":"att-1","policyId":"pol-1","environmentId":"env-1","createdAt":"2024-01-01T00:00:00Z"}`))
	}))
	defer srv.Close()

	client := provider.NewClient(srv.URL, "")
	att, err := client.CreatePolicyAttachment(t.Context(), provider.PolicyAttachmentRequest{
		PolicyID: "pol-1", EnvironmentID: "env-1",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if att.PolicyID != "pol-1" || att.EnvironmentID != "env-1" {
		t.Errorf("unexpected attachment: %+v", att)
	}
}

// TestClientCreateLogicalEnvironment_Success verifies logical environment creation.
func TestClientCreateLogicalEnvironment_Success(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		_, _ = w.Write([]byte(`{"id":"le-1","name":"prod-group","description":"desc","createdAt":"2024-01-01T00:00:00Z","updatedAt":"2024-01-01T00:00:00Z"}`))
	}))
	defer srv.Close()

	client := provider.NewClient(srv.URL, "")
	le, err := client.CreateLogicalEnvironment(t.Context(), provider.LogicalEnvironmentRequest{
		Name: "prod-group", Description: "desc",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if le.Name != "prod-group" {
		t.Errorf("expected name prod-group, got %s", le.Name)
	}
}

// TestClientAPIError verifies that non-2xx responses return errors.
func TestClientAPIError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusConflict)
		_, _ = w.Write([]byte(`{"message":"Flow with name 'x' already exists"}`))
	}))
	defer srv.Close()

	client := provider.NewClient(srv.URL, "")
	_, err := client.CreateFlow(t.Context(), provider.FlowRequest{Name: "x"})
	if err == nil {
		t.Fatal("expected error for 409 conflict, got nil")
	}
	if !strings.Contains(err.Error(), "already exists") {
		t.Errorf("expected conflict message in error, got: %v", err)
	}
}

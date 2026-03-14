package provider_test

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/MaximumTrainer/factstore-terraform-provider/internal/provider"
)

func TestNewClient(t *testing.T) {
	client := provider.NewClient("http://localhost:8080", "test-token")
	if client == nil {
		t.Fatal("expected non-nil client")
	}
}

func TestClientGetFlow_NotFound(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		_, _ = w.Write([]byte(`{"message":"not found"}`))
	}))
	defer srv.Close()

	client := provider.NewClient(srv.URL, "")
	flow, err := client.GetFlow(t.Context(), "unknown-id")
	if err != nil {
		t.Fatalf("expected no error for 404, got: %v", err)
	}
	if flow != nil {
		t.Fatal("expected nil flow for 404")
	}
}

func TestClientCreateFlow_Success(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Errorf("expected POST, got %s", r.Method)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		_, _ = w.Write([]byte(`{"id":"abc-123","name":"my-flow","description":"test","requiredAttestationTypes":["junit"],"createdAt":"2024-01-01T00:00:00Z","updatedAt":"2024-01-01T00:00:00Z"}`))
	}))
	defer srv.Close()

	client := provider.NewClient(srv.URL, "token")
	flow, err := client.CreateFlow(t.Context(), provider.FlowRequest{
		Name:                    "my-flow",
		Description:             "test",
		RequiredAttestationTypes: []string{"junit"},
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if flow.ID != "abc-123" {
		t.Errorf("expected ID abc-123, got %s", flow.ID)
	}
	if flow.Name != "my-flow" {
		t.Errorf("expected name my-flow, got %s", flow.Name)
	}
}

func TestClientCreateEnvironment_Success(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		_, _ = w.Write([]byte(`{"id":"env-1","name":"production","type":"K8S","description":"prod cluster","createdAt":"2024-01-01T00:00:00Z","updatedAt":"2024-01-01T00:00:00Z"}`))
	}))
	defer srv.Close()

	client := provider.NewClient(srv.URL, "")
	env, err := client.CreateEnvironment(t.Context(), provider.EnvironmentRequest{
		Name: "production", Type: "K8S", Description: "prod cluster",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if env.Type != "K8S" {
		t.Errorf("expected type K8S, got %s", env.Type)
	}
}

func TestClientCreatePolicy_Success(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		_, _ = w.Write([]byte(`{"id":"pol-1","name":"prod-policy","enforceProvenance":true,"enforceTrailCompliance":true,"requiredAttestationTypes":["snyk"],"createdAt":"2024-01-01T00:00:00Z","updatedAt":"2024-01-01T00:00:00Z"}`))
	}))
	defer srv.Close()

	client := provider.NewClient(srv.URL, "")
	pol, err := client.CreatePolicy(t.Context(), provider.PolicyRequest{
		Name: "prod-policy", EnforceProvenance: true, EnforceTrailCompliance: true,
		RequiredAttestationTypes: []string{"snyk"},
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !pol.EnforceProvenance {
		t.Error("expected EnforceProvenance to be true")
	}
}

func TestClientCreateOrganisation_Success(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		_, _ = w.Write([]byte(`{"id":"org-1","slug":"my-org","name":"My Org","description":"","createdAt":"2024-01-01T00:00:00Z","updatedAt":"2024-01-01T00:00:00Z"}`))
	}))
	defer srv.Close()

	client := provider.NewClient(srv.URL, "")
	org, err := client.CreateOrganisation(t.Context(), provider.OrganisationRequest{
		Slug: "my-org", Name: "My Org",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if org.Slug != "my-org" {
		t.Errorf("expected slug my-org, got %s", org.Slug)
	}
}

func TestClientDeleteFlow_NoContent(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	client := provider.NewClient(srv.URL, "")
	if err := client.DeleteFlow(t.Context(), "flow-id"); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
}

func TestClientGetEnvironment_NotFound(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	defer srv.Close()

	client := provider.NewClient(srv.URL, "")
	env, err := client.GetEnvironment(t.Context(), "missing")
	if err != nil {
		t.Fatalf("expected no error for 404, got: %v", err)
	}
	if env != nil {
		t.Fatal("expected nil env for 404")
	}
}

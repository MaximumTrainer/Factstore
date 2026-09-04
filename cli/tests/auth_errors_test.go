package tests

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
)

// #155 FR-11.1: `factstore login` reported a wrong key and an unreachable host as one error,
// "authentication or connectivity failed". They need different fixes, so they are now
// different errors with actionable guidance.

func TestUnauthenticatedErrorCarriesReasonAndGuidance(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("WWW-Authenticate", `Bearer realm="factstore"`)
		w.WriteHeader(http.StatusUnauthorized)
		w.Write([]byte(`{"error":"Unauthorized","reason":"EXPIRED",` +
			`"message":"The credential has expired","credentialPrefix":"fsp_abcde12"}`))
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	_, err := api.WhoAmI(c)
	if err == nil {
		t.Fatal("expected an error")
	}

	var apiErr *client.APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("expected a *client.APIError, got %T", err)
	}
	if !apiErr.IsUnauthenticated() {
		t.Error("expected IsUnauthenticated")
	}
	if apiErr.IsForbidden() {
		t.Error("a 401 is not a 403")
	}
	if apiErr.Reason != "EXPIRED" {
		t.Errorf("expected reason EXPIRED, got %q", apiErr.Reason)
	}
	if apiErr.CredentialPrefix != "fsp_abcde12" {
		t.Errorf("expected the prefix for diagnosis, got %q", apiErr.CredentialPrefix)
	}
	if guidance := apiErr.Guidance(); guidance == "" {
		t.Error("an expired credential should come with a next step")
	}
}

func TestForbiddenGuidanceTalksAboutScopes(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusForbidden)
		w.Write([]byte(`{"error":"Forbidden","message":"You do not have permission"}`))
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	_, err := api.WhoAmI(c)

	var apiErr *client.APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("expected a *client.APIError, got %T", err)
	}
	if !apiErr.IsForbidden() || apiErr.IsUnauthenticated() {
		t.Error("a 403 must be distinguishable from a 401")
	}
	if guidance := apiErr.Guidance(); guidance == "" {
		t.Fatal("expected guidance")
	} else if !contains(guidance, "scope") {
		t.Errorf("403 guidance should mention scopes, got %q", guidance)
	}
}

func TestRateLimitedIsItsOwnCase(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Retry-After", "30")
		w.WriteHeader(http.StatusTooManyRequests)
		w.Write([]byte(`{"error":"Too Many Requests","message":"Too many failed attempts"}`))
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	_, err := api.WhoAmI(c)

	var apiErr *client.APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("expected a *client.APIError, got %T", err)
	}
	if !apiErr.IsRateLimited() {
		t.Error("expected IsRateLimited")
	}
	if apiErr.Guidance() == "" {
		t.Error("expected guidance for a rate-limited caller")
	}
}

func TestWhoAmIReportsAnApiKeyPrincipal(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/auth/me" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"type":"API_KEY","ownerId":"owner-1",` +
			`"permissions":["attestations:write","trails:write"],"organisations":[]}`))
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	principal, err := api.WhoAmI(c)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if principal.Type != "API_KEY" || principal.OwnerID != "owner-1" {
		t.Errorf("unexpected principal: %+v", principal)
	}
	if len(principal.Permissions) != 2 {
		t.Errorf("expected the scopes to come back, got %v", principal.Permissions)
	}
}

func TestWhoAmIReportsAUserPrincipal(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"type":"USER","userId":"u1","email":"a@example.com","name":"A",` +
			`"orgSlug":"acme","role":"ADMIN","permissions":["admin"],` +
			`"organisations":[{"orgSlug":"acme","role":"ADMIN"}]}`))
	}))
	defer server.Close()

	c := mustNewClient(t, server.URL, "tok")
	principal, err := api.WhoAmI(c)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if principal.Role != "ADMIN" || principal.OrgSlug != "acme" {
		t.Errorf("unexpected principal: %+v", principal)
	}
	if len(principal.Organisations) != 1 {
		t.Errorf("expected the organisation list, got %v", principal.Organisations)
	}
}

func contains(haystack, needle string) bool {
	return len(haystack) >= len(needle) && (func() bool {
		for i := 0; i+len(needle) <= len(haystack); i++ {
			if haystack[i:i+len(needle)] == needle {
				return true
			}
		}
		return false
	})()
}

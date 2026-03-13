package provider

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

// Client is the Factstore API HTTP client used by the Terraform provider.
type Client struct {
	baseURL    string
	apiToken   string
	httpClient *http.Client
}

// NewClient constructs a new Factstore API client.
func NewClient(baseURL, apiToken string) *Client {
	return &Client{
		baseURL:  baseURL,
		apiToken: apiToken,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

func (c *Client) doRequest(ctx context.Context, method, path string, body interface{}) ([]byte, int, error) {
	var reqBody io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			return nil, 0, fmt.Errorf("marshaling request body: %w", err)
		}
		reqBody = bytes.NewReader(b)
	}

	req, err := http.NewRequestWithContext(ctx, method, c.baseURL+path, reqBody)
	if err != nil {
		return nil, 0, fmt.Errorf("creating request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	if c.apiToken != "" {
		req.Header.Set("Authorization", "Bearer "+c.apiToken)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, 0, fmt.Errorf("executing request: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, resp.StatusCode, fmt.Errorf("reading response body: %w", err)
	}

	return respBody, resp.StatusCode, nil
}

// apiError represents an error response from the Factstore API.
type apiError struct {
	Message string `json:"message"`
	Error   string `json:"error"`
}

func parseAPIError(body []byte, statusCode int) error {
	var errResp apiError
	if err := json.Unmarshal(body, &errResp); err == nil && errResp.Message != "" {
		return fmt.Errorf("API error %d: %s", statusCode, errResp.Message)
	}
	return fmt.Errorf("API error %d: %s", statusCode, string(body))
}

// ---- Flow API ----

type FlowRequest struct {
	Name                    string   `json:"name"`
	Description             string   `json:"description"`
	RequiredAttestationTypes []string `json:"requiredAttestationTypes"`
}

type FlowUpdateRequest struct {
	Name                    *string  `json:"name,omitempty"`
	Description             *string  `json:"description,omitempty"`
	RequiredAttestationTypes []string `json:"requiredAttestationTypes,omitempty"`
}

type FlowResponse struct {
	ID                      string   `json:"id"`
	Name                    string   `json:"name"`
	Description             string   `json:"description"`
	RequiredAttestationTypes []string `json:"requiredAttestationTypes"`
	CreatedAt               string   `json:"createdAt"`
	UpdatedAt               string   `json:"updatedAt"`
}

func (c *Client) CreateFlow(ctx context.Context, req FlowRequest) (*FlowResponse, error) {
	body, status, err := c.doRequest(ctx, http.MethodPost, "/api/v1/flows", req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusCreated {
		return nil, parseAPIError(body, status)
	}
	var resp FlowResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parsing flow response: %w", err)
	}
	return &resp, nil
}

func (c *Client) GetFlow(ctx context.Context, id string) (*FlowResponse, error) {
	body, status, err := c.doRequest(ctx, http.MethodGet, "/api/v1/flows/"+id, nil)
	if err != nil {
		return nil, err
	}
	if status == http.StatusNotFound {
		return nil, nil
	}
	if status != http.StatusOK {
		return nil, parseAPIError(body, status)
	}
	var resp FlowResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parsing flow response: %w", err)
	}
	return &resp, nil
}

func (c *Client) UpdateFlow(ctx context.Context, id string, req FlowUpdateRequest) (*FlowResponse, error) {
	body, status, err := c.doRequest(ctx, http.MethodPut, "/api/v1/flows/"+id, req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, parseAPIError(body, status)
	}
	var resp FlowResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parsing flow response: %w", err)
	}
	return &resp, nil
}

func (c *Client) DeleteFlow(ctx context.Context, id string) error {
	_, status, err := c.doRequest(ctx, http.MethodDelete, "/api/v1/flows/"+id, nil)
	if err != nil {
		return err
	}
	if status != http.StatusNoContent && status != http.StatusNotFound {
		return fmt.Errorf("unexpected status code %d deleting flow", status)
	}
	return nil
}

// ---- Environment API ----

type EnvironmentRequest struct {
	Name        string `json:"name"`
	Type        string `json:"type"`
	Description string `json:"description"`
}

type EnvironmentUpdateRequest struct {
	Name        *string `json:"name,omitempty"`
	Type        *string `json:"type,omitempty"`
	Description *string `json:"description,omitempty"`
}

type EnvironmentResponse struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Type        string `json:"type"`
	Description string `json:"description"`
	CreatedAt   string `json:"createdAt"`
	UpdatedAt   string `json:"updatedAt"`
}

func (c *Client) CreateEnvironment(ctx context.Context, req EnvironmentRequest) (*EnvironmentResponse, error) {
	body, status, err := c.doRequest(ctx, http.MethodPost, "/api/v1/environments", req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusCreated {
		return nil, parseAPIError(body, status)
	}
	var resp EnvironmentResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parsing environment response: %w", err)
	}
	return &resp, nil
}

func (c *Client) GetEnvironment(ctx context.Context, id string) (*EnvironmentResponse, error) {
	body, status, err := c.doRequest(ctx, http.MethodGet, "/api/v1/environments/"+id, nil)
	if err != nil {
		return nil, err
	}
	if status == http.StatusNotFound {
		return nil, nil
	}
	if status != http.StatusOK {
		return nil, parseAPIError(body, status)
	}
	var resp EnvironmentResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parsing environment response: %w", err)
	}
	return &resp, nil
}

func (c *Client) UpdateEnvironment(ctx context.Context, id string, req EnvironmentUpdateRequest) (*EnvironmentResponse, error) {
	body, status, err := c.doRequest(ctx, http.MethodPut, "/api/v1/environments/"+id, req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, parseAPIError(body, status)
	}
	var resp EnvironmentResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parsing environment response: %w", err)
	}
	return &resp, nil
}

func (c *Client) DeleteEnvironment(ctx context.Context, id string) error {
	_, status, err := c.doRequest(ctx, http.MethodDelete, "/api/v1/environments/"+id, nil)
	if err != nil {
		return err
	}
	if status != http.StatusNoContent && status != http.StatusNotFound {
		return fmt.Errorf("unexpected status code %d deleting environment", status)
	}
	return nil
}

// ---- Policy API ----

type PolicyRequest struct {
	Name                    string   `json:"name"`
	EnforceProvenance       bool     `json:"enforceProvenance"`
	EnforceTrailCompliance  bool     `json:"enforceTrailCompliance"`
	RequiredAttestationTypes []string `json:"requiredAttestationTypes"`
}

type PolicyUpdateRequest struct {
	Name                    *string  `json:"name,omitempty"`
	EnforceProvenance       *bool    `json:"enforceProvenance,omitempty"`
	EnforceTrailCompliance  *bool    `json:"enforceTrailCompliance,omitempty"`
	RequiredAttestationTypes []string `json:"requiredAttestationTypes,omitempty"`
}

type PolicyResponse struct {
	ID                      string   `json:"id"`
	Name                    string   `json:"name"`
	EnforceProvenance       bool     `json:"enforceProvenance"`
	EnforceTrailCompliance  bool     `json:"enforceTrailCompliance"`
	RequiredAttestationTypes []string `json:"requiredAttestationTypes"`
	CreatedAt               string   `json:"createdAt"`
	UpdatedAt               string   `json:"updatedAt"`
}

func (c *Client) CreatePolicy(ctx context.Context, req PolicyRequest) (*PolicyResponse, error) {
	body, status, err := c.doRequest(ctx, http.MethodPost, "/api/v1/policies", req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusCreated {
		return nil, parseAPIError(body, status)
	}
	var resp PolicyResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parsing policy response: %w", err)
	}
	return &resp, nil
}

func (c *Client) GetPolicy(ctx context.Context, id string) (*PolicyResponse, error) {
	body, status, err := c.doRequest(ctx, http.MethodGet, "/api/v1/policies/"+id, nil)
	if err != nil {
		return nil, err
	}
	if status == http.StatusNotFound {
		return nil, nil
	}
	if status != http.StatusOK {
		return nil, parseAPIError(body, status)
	}
	var resp PolicyResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parsing policy response: %w", err)
	}
	return &resp, nil
}

func (c *Client) UpdatePolicy(ctx context.Context, id string, req PolicyUpdateRequest) (*PolicyResponse, error) {
	body, status, err := c.doRequest(ctx, http.MethodPut, "/api/v1/policies/"+id, req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, parseAPIError(body, status)
	}
	var resp PolicyResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parsing policy response: %w", err)
	}
	return &resp, nil
}

func (c *Client) DeletePolicy(ctx context.Context, id string) error {
	_, status, err := c.doRequest(ctx, http.MethodDelete, "/api/v1/policies/"+id, nil)
	if err != nil {
		return err
	}
	if status != http.StatusNoContent && status != http.StatusNotFound {
		return fmt.Errorf("unexpected status code %d deleting policy", status)
	}
	return nil
}

// ---- PolicyAttachment API ----

type PolicyAttachmentRequest struct {
	PolicyID      string `json:"policyId"`
	EnvironmentID string `json:"environmentId"`
}

type PolicyAttachmentResponse struct {
	ID            string `json:"id"`
	PolicyID      string `json:"policyId"`
	EnvironmentID string `json:"environmentId"`
	CreatedAt     string `json:"createdAt"`
}

func (c *Client) CreatePolicyAttachment(ctx context.Context, req PolicyAttachmentRequest) (*PolicyAttachmentResponse, error) {
	body, status, err := c.doRequest(ctx, http.MethodPost, "/api/v1/policy-attachments", req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusCreated {
		return nil, parseAPIError(body, status)
	}
	var resp PolicyAttachmentResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parsing policy attachment response: %w", err)
	}
	return &resp, nil
}

func (c *Client) GetPolicyAttachment(ctx context.Context, id string) (*PolicyAttachmentResponse, error) {
	body, status, err := c.doRequest(ctx, http.MethodGet, "/api/v1/policy-attachments/"+id, nil)
	if err != nil {
		return nil, err
	}
	if status == http.StatusNotFound {
		return nil, nil
	}
	if status != http.StatusOK {
		return nil, parseAPIError(body, status)
	}
	var resp PolicyAttachmentResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parsing policy attachment response: %w", err)
	}
	return &resp, nil
}

func (c *Client) DeletePolicyAttachment(ctx context.Context, id string) error {
	_, status, err := c.doRequest(ctx, http.MethodDelete, "/api/v1/policy-attachments/"+id, nil)
	if err != nil {
		return err
	}
	if status != http.StatusNoContent && status != http.StatusNotFound {
		return fmt.Errorf("unexpected status code %d deleting policy attachment", status)
	}
	return nil
}

// ---- LogicalEnvironment API ----

type LogicalEnvironmentRequest struct {
	Name        string `json:"name"`
	Description string `json:"description"`
}

type LogicalEnvironmentUpdateRequest struct {
	Name        *string `json:"name,omitempty"`
	Description *string `json:"description,omitempty"`
}

type LogicalEnvironmentResponse struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description"`
	CreatedAt   string `json:"createdAt"`
	UpdatedAt   string `json:"updatedAt"`
}

func (c *Client) CreateLogicalEnvironment(ctx context.Context, req LogicalEnvironmentRequest) (*LogicalEnvironmentResponse, error) {
	body, status, err := c.doRequest(ctx, http.MethodPost, "/api/v1/logical-environments", req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusCreated {
		return nil, parseAPIError(body, status)
	}
	var resp LogicalEnvironmentResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parsing logical environment response: %w", err)
	}
	return &resp, nil
}

func (c *Client) GetLogicalEnvironment(ctx context.Context, id string) (*LogicalEnvironmentResponse, error) {
	body, status, err := c.doRequest(ctx, http.MethodGet, "/api/v1/logical-environments/"+id, nil)
	if err != nil {
		return nil, err
	}
	if status == http.StatusNotFound {
		return nil, nil
	}
	if status != http.StatusOK {
		return nil, parseAPIError(body, status)
	}
	var resp LogicalEnvironmentResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parsing logical environment response: %w", err)
	}
	return &resp, nil
}

func (c *Client) UpdateLogicalEnvironment(ctx context.Context, id string, req LogicalEnvironmentUpdateRequest) (*LogicalEnvironmentResponse, error) {
	body, status, err := c.doRequest(ctx, http.MethodPut, "/api/v1/logical-environments/"+id, req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, parseAPIError(body, status)
	}
	var resp LogicalEnvironmentResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parsing logical environment response: %w", err)
	}
	return &resp, nil
}

func (c *Client) DeleteLogicalEnvironment(ctx context.Context, id string) error {
	_, status, err := c.doRequest(ctx, http.MethodDelete, "/api/v1/logical-environments/"+id, nil)
	if err != nil {
		return err
	}
	if status != http.StatusNoContent && status != http.StatusNotFound {
		return fmt.Errorf("unexpected status code %d deleting logical environment", status)
	}
	return nil
}

// ---- Organisation API ----

type OrganisationRequest struct {
	Slug        string `json:"slug"`
	Name        string `json:"name"`
	Description string `json:"description"`
}

type OrganisationUpdateRequest struct {
	Name        *string `json:"name,omitempty"`
	Description *string `json:"description,omitempty"`
}

type OrganisationResponse struct {
	ID          string `json:"id"`
	Slug        string `json:"slug"`
	Name        string `json:"name"`
	Description string `json:"description"`
	CreatedAt   string `json:"createdAt"`
	UpdatedAt   string `json:"updatedAt"`
}

func (c *Client) CreateOrganisation(ctx context.Context, req OrganisationRequest) (*OrganisationResponse, error) {
	body, status, err := c.doRequest(ctx, http.MethodPost, "/api/v1/organisations", req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusCreated {
		return nil, parseAPIError(body, status)
	}
	var resp OrganisationResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parsing organisation response: %w", err)
	}
	return &resp, nil
}

func (c *Client) GetOrganisation(ctx context.Context, id string) (*OrganisationResponse, error) {
	body, status, err := c.doRequest(ctx, http.MethodGet, "/api/v1/organisations/"+id, nil)
	if err != nil {
		return nil, err
	}
	if status == http.StatusNotFound {
		return nil, nil
	}
	if status != http.StatusOK {
		return nil, parseAPIError(body, status)
	}
	var resp OrganisationResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parsing organisation response: %w", err)
	}
	return &resp, nil
}

func (c *Client) UpdateOrganisation(ctx context.Context, id string, req OrganisationUpdateRequest) (*OrganisationResponse, error) {
	body, status, err := c.doRequest(ctx, http.MethodPut, "/api/v1/organisations/"+id, req)
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, parseAPIError(body, status)
	}
	var resp OrganisationResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parsing organisation response: %w", err)
	}
	return &resp, nil
}

func (c *Client) DeleteOrganisation(ctx context.Context, id string) error {
	_, status, err := c.doRequest(ctx, http.MethodDelete, "/api/v1/organisations/"+id, nil)
	if err != nil {
		return err
	}
	if status != http.StatusNoContent && status != http.StatusNotFound {
		return fmt.Errorf("unexpected status code %d deleting organisation", status)
	}
	return nil
}

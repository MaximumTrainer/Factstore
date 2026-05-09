package provider

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"

	"github.com/hashicorp/terraform-plugin-framework/resource"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema/planmodifier"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema/stringplanmodifier"
	"github.com/hashicorp/terraform-plugin-framework/types"
	"github.com/hashicorp/terraform-plugin-log/tflog"
)

var _ resource.Resource = &environmentResource{}
var _ resource.ResourceWithConfigure = &environmentResource{}

type environmentResource struct {
	endpoint string
}

type environmentResourceModel struct {
	ID          types.String `tfsdk:"id"`
	Name        types.String `tfsdk:"name"`
	Description types.String `tfsdk:"description"`
	OrgSlug     types.String `tfsdk:"org_slug"`
}

type environmentCreateRequest struct {
	Name        string `json:"name"`
	Type        string `json:"type"`
	Description string `json:"description,omitempty"`
	OrgSlug     string `json:"orgSlug,omitempty"`
}

type environmentUpdateRequest struct {
	Name        *string `json:"name,omitempty"`
	Description *string `json:"description,omitempty"`
}

type environmentAPIResponse struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Type        string `json:"type"`
	Description string `json:"description"`
	OrgSlug     string `json:"orgSlug,omitempty"`
}

func NewEnvironmentResource() resource.Resource {
	return &environmentResource{}
}

func (r *environmentResource) Metadata(_ context.Context, req resource.MetadataRequest, resp *resource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_environment"
}

func (r *environmentResource) Schema(_ context.Context, _ resource.SchemaRequest, resp *resource.SchemaResponse) {
	resp.Schema = schema.Schema{
		Description: "Manages a Factstore Environment.",
		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Computed:    true,
				Description: "The unique identifier of the environment.",
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"name": schema.StringAttribute{
				Required:    true,
				Description: "The name of the environment.",
			},
			"description": schema.StringAttribute{
				Optional:    true,
				Description: "A description for the environment.",
			},
			"org_slug": schema.StringAttribute{
				Required:    true,
				Description: "The organisation slug this environment belongs to.",
			},
		},
	}
}

func (r *environmentResource) Configure(_ context.Context, req resource.ConfigureRequest, resp *resource.ConfigureResponse) {
	if req.ProviderData == nil {
		return
	}
	endpoint, ok := req.ProviderData.(string)
	if !ok {
		resp.Diagnostics.AddError("Unexpected Provider Data", fmt.Sprintf("Expected string, got %T", req.ProviderData))
		return
	}
	r.endpoint = endpoint
}

func (r *environmentResource) Create(ctx context.Context, req resource.CreateRequest, resp *resource.CreateResponse) {
	var plan environmentResourceModel
	diags := req.Plan.Get(ctx, &plan)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	body := environmentCreateRequest{
		Name:        plan.Name.ValueString(),
		Type:        "GENERIC",
		Description: plan.Description.ValueString(),
		OrgSlug:     plan.OrgSlug.ValueString(),
	}

	apiResp, err := r.doRequest(ctx, http.MethodPost, r.endpoint+"/api/v1/environments", body)
	if err != nil {
		resp.Diagnostics.AddError("Error creating environment", err.Error())
		return
	}

	plan.ID = types.StringValue(apiResp.ID)
	plan.Name = types.StringValue(apiResp.Name)
	plan.Description = types.StringValue(apiResp.Description)
	plan.OrgSlug = types.StringValue(apiResp.OrgSlug)

	tflog.Trace(ctx, "created environment", map[string]interface{}{"id": apiResp.ID})

	resp.Diagnostics.Append(resp.State.Set(ctx, &plan)...)
}

func (r *environmentResource) Read(ctx context.Context, req resource.ReadRequest, resp *resource.ReadResponse) {
	var state environmentResourceModel
	diags := req.State.Get(ctx, &state)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	apiResp, err := r.doRequest(ctx, http.MethodGet, fmt.Sprintf("%s/api/v1/environments/%s", r.endpoint, state.ID.ValueString()), nil)
	if err != nil {
		resp.Diagnostics.AddError("Error reading environment", err.Error())
		return
	}

	state.Name = types.StringValue(apiResp.Name)
	state.Description = types.StringValue(apiResp.Description)
	state.OrgSlug = types.StringValue(apiResp.OrgSlug)

	resp.Diagnostics.Append(resp.State.Set(ctx, &state)...)
}

func (r *environmentResource) Update(ctx context.Context, req resource.UpdateRequest, resp *resource.UpdateResponse) {
	var plan environmentResourceModel
	diags := req.Plan.Get(ctx, &plan)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	var state environmentResourceModel
	diags = req.State.Get(ctx, &state)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	name := plan.Name.ValueString()
	description := plan.Description.ValueString()
	body := environmentUpdateRequest{
		Name:        &name,
		Description: &description,
	}

	apiResp, err := r.doRequest(ctx, http.MethodPut, fmt.Sprintf("%s/api/v1/environments/%s", r.endpoint, state.ID.ValueString()), body)
	if err != nil {
		resp.Diagnostics.AddError("Error updating environment", err.Error())
		return
	}

	plan.ID = state.ID
	plan.Name = types.StringValue(apiResp.Name)
	plan.Description = types.StringValue(apiResp.Description)
	plan.OrgSlug = types.StringValue(apiResp.OrgSlug)

	resp.Diagnostics.Append(resp.State.Set(ctx, &plan)...)
}

// Delete removes the resource from state only; the Factstore API has no delete endpoint for environments.
func (r *environmentResource) Delete(_ context.Context, _ resource.DeleteRequest, _ *resource.DeleteResponse) {
}

func (r *environmentResource) doRequest(ctx context.Context, method, url string, body interface{}) (*environmentAPIResponse, error) {
	var reqBody io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return nil, fmt.Errorf("marshalling request: %w", err)
		}
		reqBody = bytes.NewReader(data)
	}

	httpReq, err := http.NewRequestWithContext(ctx, method, url, reqBody)
	if err != nil {
		return nil, fmt.Errorf("creating request: %w", err)
	}
	if body != nil {
		httpReq.Header.Set("Content-Type", "application/json")
	}
	httpReq.Header.Set("Accept", "application/json")

	httpResp, err := http.DefaultClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("executing request: %w", err)
	}
	defer httpResp.Body.Close()

	respData, err := io.ReadAll(httpResp.Body)
	if err != nil {
		return nil, fmt.Errorf("reading response body: %w", err)
	}

	if httpResp.StatusCode < 200 || httpResp.StatusCode >= 300 {
		return nil, fmt.Errorf("unexpected status %d: %s", httpResp.StatusCode, string(respData))
	}

	var result environmentAPIResponse
	if err := json.Unmarshal(respData, &result); err != nil {
		return nil, fmt.Errorf("decoding response: %w", err)
	}
	return &result, nil
}

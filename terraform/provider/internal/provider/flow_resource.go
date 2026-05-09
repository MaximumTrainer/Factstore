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

var _ resource.Resource = &flowResource{}
var _ resource.ResourceWithConfigure = &flowResource{}

type flowResource struct {
	endpoint string
}

type flowResourceModel struct {
	ID          types.String `tfsdk:"id"`
	Name        types.String `tfsdk:"name"`
	Description types.String `tfsdk:"description"`
	OrgSlug     types.String `tfsdk:"org_slug"`
}

type flowCreateRequest struct {
	Name        string `json:"name"`
	Description string `json:"description,omitempty"`
	OrgSlug     string `json:"orgSlug,omitempty"`
}

type flowUpdateRequest struct {
	Name        *string `json:"name,omitempty"`
	Description *string `json:"description,omitempty"`
}

type flowAPIResponse struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description"`
	OrgSlug     string `json:"orgSlug,omitempty"`
}

func NewFlowResource() resource.Resource {
	return &flowResource{}
}

func (r *flowResource) Metadata(_ context.Context, req resource.MetadataRequest, resp *resource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_flow"
}

func (r *flowResource) Schema(_ context.Context, _ resource.SchemaRequest, resp *resource.SchemaResponse) {
	resp.Schema = schema.Schema{
		Description: "Manages a Factstore Flow.",
		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Computed:    true,
				Description: "The unique identifier of the flow.",
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"name": schema.StringAttribute{
				Required:    true,
				Description: "The name of the flow.",
			},
			"description": schema.StringAttribute{
				Optional:    true,
				Description: "A description for the flow.",
			},
			"org_slug": schema.StringAttribute{
				Required:    true,
				Description: "The organisation slug this flow belongs to.",
			},
		},
	}
}

func (r *flowResource) Configure(_ context.Context, req resource.ConfigureRequest, resp *resource.ConfigureResponse) {
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

func (r *flowResource) Create(ctx context.Context, req resource.CreateRequest, resp *resource.CreateResponse) {
	var plan flowResourceModel
	diags := req.Plan.Get(ctx, &plan)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	body := flowCreateRequest{
		Name:        plan.Name.ValueString(),
		Description: plan.Description.ValueString(),
		OrgSlug:     plan.OrgSlug.ValueString(),
	}

	apiResp, err := r.doRequest(ctx, http.MethodPost, r.endpoint+"/api/v1/flows", body)
	if err != nil {
		resp.Diagnostics.AddError("Error creating flow", err.Error())
		return
	}

	plan.ID = types.StringValue(apiResp.ID)
	plan.Name = types.StringValue(apiResp.Name)
	plan.Description = types.StringValue(apiResp.Description)
	plan.OrgSlug = types.StringValue(apiResp.OrgSlug)

	tflog.Trace(ctx, "created flow", map[string]interface{}{"id": apiResp.ID})

	resp.Diagnostics.Append(resp.State.Set(ctx, &plan)...)
}

func (r *flowResource) Read(ctx context.Context, req resource.ReadRequest, resp *resource.ReadResponse) {
	var state flowResourceModel
	diags := req.State.Get(ctx, &state)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	apiResp, err := r.doRequest(ctx, http.MethodGet, fmt.Sprintf("%s/api/v1/flows/%s", r.endpoint, state.ID.ValueString()), nil)
	if err != nil {
		resp.Diagnostics.AddError("Error reading flow", err.Error())
		return
	}

	state.Name = types.StringValue(apiResp.Name)
	state.Description = types.StringValue(apiResp.Description)
	state.OrgSlug = types.StringValue(apiResp.OrgSlug)

	resp.Diagnostics.Append(resp.State.Set(ctx, &state)...)
}

func (r *flowResource) Update(ctx context.Context, req resource.UpdateRequest, resp *resource.UpdateResponse) {
	var plan flowResourceModel
	diags := req.Plan.Get(ctx, &plan)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	var state flowResourceModel
	diags = req.State.Get(ctx, &state)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	name := plan.Name.ValueString()
	description := plan.Description.ValueString()
	body := flowUpdateRequest{
		Name:        &name,
		Description: &description,
	}

	apiResp, err := r.doRequest(ctx, http.MethodPut, fmt.Sprintf("%s/api/v1/flows/%s", r.endpoint, state.ID.ValueString()), body)
	if err != nil {
		resp.Diagnostics.AddError("Error updating flow", err.Error())
		return
	}

	plan.ID = state.ID
	plan.Name = types.StringValue(apiResp.Name)
	plan.Description = types.StringValue(apiResp.Description)
	plan.OrgSlug = types.StringValue(apiResp.OrgSlug)

	resp.Diagnostics.Append(resp.State.Set(ctx, &plan)...)
}

// Delete removes the resource from state only; the Factstore API has no delete endpoint for flows.
func (r *flowResource) Delete(_ context.Context, _ resource.DeleteRequest, _ *resource.DeleteResponse) {
}

func (r *flowResource) doRequest(ctx context.Context, method, url string, body interface{}) (*flowAPIResponse, error) {
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

	var result flowAPIResponse
	if err := json.Unmarshal(respData, &result); err != nil {
		return nil, fmt.Errorf("decoding response: %w", err)
	}
	return &result, nil
}

package provider

import (
	"context"
	"fmt"

	"github.com/hashicorp/terraform-plugin-framework/attr"
	"github.com/hashicorp/terraform-plugin-framework/diag"
	"github.com/hashicorp/terraform-plugin-framework/resource"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema/boolplanmodifier"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema/planmodifier"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema/stringplanmodifier"
	"github.com/hashicorp/terraform-plugin-framework/types"
)

var _ resource.Resource = &PolicyResource{}
var _ resource.ResourceWithImportState = &PolicyResource{}

func NewPolicyResource() resource.Resource {
	return &PolicyResource{}
}

type PolicyResource struct {
	client *Client
}

type PolicyResourceModel struct {
	ID                      types.String `tfsdk:"id"`
	Name                    types.String `tfsdk:"name"`
	EnforceProvenance       types.Bool   `tfsdk:"enforce_provenance"`
	EnforceTrailCompliance  types.Bool   `tfsdk:"enforce_trail_compliance"`
	RequiredAttestationTypes types.List   `tfsdk:"required_attestation_types"`
	CreatedAt               types.String `tfsdk:"created_at"`
	UpdatedAt               types.String `tfsdk:"updated_at"`
}

func (r *PolicyResource) Metadata(_ context.Context, req resource.MetadataRequest, resp *resource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_policy"
}

func (r *PolicyResource) Schema(_ context.Context, _ resource.SchemaRequest, resp *resource.SchemaResponse) {
	resp.Schema = schema.Schema{
		MarkdownDescription: "Manages a Factstore Policy. Policies define compliance requirements for environments.",
		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The unique identifier of the policy.",
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"name": schema.StringAttribute{
				Required:            true,
				MarkdownDescription: "The unique name of the policy.",
			},
			"enforce_provenance": schema.BoolAttribute{
				Optional:            true,
				Computed:            true,
				MarkdownDescription: "Whether to enforce artifact provenance checks.",
				PlanModifiers: []planmodifier.Bool{
					boolplanmodifier.UseStateForUnknown(),
				},
			},
			"enforce_trail_compliance": schema.BoolAttribute{
				Optional:            true,
				Computed:            true,
				MarkdownDescription: "Whether to enforce trail compliance checks.",
				PlanModifiers: []planmodifier.Bool{
					boolplanmodifier.UseStateForUnknown(),
				},
			},
			"required_attestation_types": schema.ListAttribute{
				Optional:            true,
				Computed:            true,
				ElementType:         types.StringType,
				MarkdownDescription: "List of attestation types required by this policy.",
			},
			"created_at": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The timestamp when the policy was created.",
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"updated_at": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The timestamp when the policy was last updated.",
			},
		},
	}
}

func (r *PolicyResource) Configure(_ context.Context, req resource.ConfigureRequest, resp *resource.ConfigureResponse) {
	if req.ProviderData == nil {
		return
	}
	client, ok := req.ProviderData.(*Client)
	if !ok {
		resp.Diagnostics.AddError("Unexpected Provider Data Type",
			fmt.Sprintf("Expected *Client, got: %T", req.ProviderData))
		return
	}
	r.client = client
}

func (r *PolicyResource) Create(ctx context.Context, req resource.CreateRequest, resp *resource.CreateResponse) {
	var plan PolicyResourceModel
	resp.Diagnostics.Append(req.Plan.Get(ctx, &plan)...)
	if resp.Diagnostics.HasError() {
		return
	}

	attestationTypes := listToStringSlice(ctx, plan.RequiredAttestationTypes, &resp.Diagnostics)
	if resp.Diagnostics.HasError() {
		return
	}

	policy, err := r.client.CreatePolicy(ctx, PolicyRequest{
		Name:                    plan.Name.ValueString(),
		EnforceProvenance:       plan.EnforceProvenance.ValueBool(),
		EnforceTrailCompliance:  plan.EnforceTrailCompliance.ValueBool(),
		RequiredAttestationTypes: attestationTypes,
	})
	if err != nil {
		resp.Diagnostics.AddError("Error creating policy", err.Error())
		return
	}

	resp.Diagnostics.Append(setPolicyState(&plan, policy)...)
	resp.Diagnostics.Append(resp.State.Set(ctx, &plan)...)
}

func (r *PolicyResource) Read(ctx context.Context, req resource.ReadRequest, resp *resource.ReadResponse) {
	var state PolicyResourceModel
	resp.Diagnostics.Append(req.State.Get(ctx, &state)...)
	if resp.Diagnostics.HasError() {
		return
	}

	policy, err := r.client.GetPolicy(ctx, state.ID.ValueString())
	if err != nil {
		resp.Diagnostics.AddError("Error reading policy", err.Error())
		return
	}
	if policy == nil {
		resp.State.RemoveResource(ctx)
		return
	}

	resp.Diagnostics.Append(setPolicyState(&state, policy)...)
	resp.Diagnostics.Append(resp.State.Set(ctx, &state)...)
}

func (r *PolicyResource) Update(ctx context.Context, req resource.UpdateRequest, resp *resource.UpdateResponse) {
	var plan PolicyResourceModel
	resp.Diagnostics.Append(req.Plan.Get(ctx, &plan)...)
	if resp.Diagnostics.HasError() {
		return
	}

	attestationTypes := listToStringSlice(ctx, plan.RequiredAttestationTypes, &resp.Diagnostics)
	if resp.Diagnostics.HasError() {
		return
	}

	name := plan.Name.ValueString()
	enforceProvenance := plan.EnforceProvenance.ValueBool()
	enforceTrailCompliance := plan.EnforceTrailCompliance.ValueBool()
	policy, err := r.client.UpdatePolicy(ctx, plan.ID.ValueString(), PolicyUpdateRequest{
		Name:                    &name,
		EnforceProvenance:       &enforceProvenance,
		EnforceTrailCompliance:  &enforceTrailCompliance,
		RequiredAttestationTypes: attestationTypes,
	})
	if err != nil {
		resp.Diagnostics.AddError("Error updating policy", err.Error())
		return
	}

	resp.Diagnostics.Append(setPolicyState(&plan, policy)...)
	resp.Diagnostics.Append(resp.State.Set(ctx, &plan)...)
}

func (r *PolicyResource) Delete(ctx context.Context, req resource.DeleteRequest, resp *resource.DeleteResponse) {
	var state PolicyResourceModel
	resp.Diagnostics.Append(req.State.Get(ctx, &state)...)
	if resp.Diagnostics.HasError() {
		return
	}

	if err := r.client.DeletePolicy(ctx, state.ID.ValueString()); err != nil {
		resp.Diagnostics.AddError("Error deleting policy", err.Error())
	}
}

func (r *PolicyResource) ImportState(ctx context.Context, req resource.ImportStateRequest, resp *resource.ImportStateResponse) {
	policy, err := r.client.GetPolicy(ctx, req.ID)
	if err != nil {
		resp.Diagnostics.AddError("Error importing policy", err.Error())
		return
	}
	if policy == nil {
		resp.Diagnostics.AddError("Policy not found", fmt.Sprintf("No policy found with ID: %s", req.ID))
		return
	}

	var state PolicyResourceModel
	resp.Diagnostics.Append(setPolicyState(&state, policy)...)
	resp.Diagnostics.Append(resp.State.Set(ctx, &state)...)
}

func setPolicyState(state *PolicyResourceModel, policy *PolicyResponse) diag.Diagnostics {
	state.ID = types.StringValue(policy.ID)
	state.Name = types.StringValue(policy.Name)
	state.EnforceProvenance = types.BoolValue(policy.EnforceProvenance)
	state.EnforceTrailCompliance = types.BoolValue(policy.EnforceTrailCompliance)
	state.CreatedAt = types.StringValue(policy.CreatedAt)
	state.UpdatedAt = types.StringValue(policy.UpdatedAt)

	attrVals := make([]attr.Value, len(policy.RequiredAttestationTypes))
	for i, t := range policy.RequiredAttestationTypes {
		attrVals[i] = types.StringValue(t)
	}
	listVal, diags := types.ListValue(types.StringType, attrVals)
	state.RequiredAttestationTypes = listVal
	return diags
}

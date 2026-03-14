package provider

import (
	"context"
	"fmt"

	"github.com/hashicorp/terraform-plugin-framework/attr"
	"github.com/hashicorp/terraform-plugin-framework/diag"
	"github.com/hashicorp/terraform-plugin-framework/resource"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema/planmodifier"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema/stringplanmodifier"
	"github.com/hashicorp/terraform-plugin-framework/types"
)

var _ resource.Resource = &FlowResource{}
var _ resource.ResourceWithImportState = &FlowResource{}

func NewFlowResource() resource.Resource {
	return &FlowResource{}
}

type FlowResource struct {
	client *Client
}

type FlowResourceModel struct {
	ID                      types.String `tfsdk:"id"`
	Name                    types.String `tfsdk:"name"`
	Description             types.String `tfsdk:"description"`
	RequiredAttestationTypes types.List   `tfsdk:"required_attestation_types"`
	CreatedAt               types.String `tfsdk:"created_at"`
	UpdatedAt               types.String `tfsdk:"updated_at"`
}

func (r *FlowResource) Metadata(_ context.Context, req resource.MetadataRequest, resp *resource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_flow"
}

func (r *FlowResource) Schema(_ context.Context, _ resource.SchemaRequest, resp *resource.SchemaResponse) {
	resp.Schema = schema.Schema{
		MarkdownDescription: "Manages a Factstore Flow. A Flow defines a compliance pipeline with required attestation types.",
		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The unique identifier of the flow.",
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"name": schema.StringAttribute{
				Required:            true,
				MarkdownDescription: "The unique name of the flow.",
			},
			"description": schema.StringAttribute{
				Optional:            true,
				Computed:            true,
				MarkdownDescription: "A human-readable description of the flow.",
			},
			"required_attestation_types": schema.ListAttribute{
				Optional:            true,
				Computed:            true,
				ElementType:         types.StringType,
				MarkdownDescription: "List of attestation types required for trail compliance (e.g. `junit`, `snyk`, `pull-request`).",
			},
			"created_at": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The timestamp when the flow was created.",
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"updated_at": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The timestamp when the flow was last updated.",
			},
		},
	}
}

func (r *FlowResource) Configure(_ context.Context, req resource.ConfigureRequest, resp *resource.ConfigureResponse) {
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

func (r *FlowResource) Create(ctx context.Context, req resource.CreateRequest, resp *resource.CreateResponse) {
	var plan FlowResourceModel
	resp.Diagnostics.Append(req.Plan.Get(ctx, &plan)...)
	if resp.Diagnostics.HasError() {
		return
	}

	attestationTypes := listToStringSlice(ctx, plan.RequiredAttestationTypes, &resp.Diagnostics)
	if resp.Diagnostics.HasError() {
		return
	}

	flow, err := r.client.CreateFlow(ctx, FlowRequest{
		Name:                    plan.Name.ValueString(),
		Description:             plan.Description.ValueString(),
		RequiredAttestationTypes: attestationTypes,
	})
	if err != nil {
		resp.Diagnostics.AddError("Error creating flow", err.Error())
		return
	}

	resp.Diagnostics.Append(setFlowState(&plan, flow)...)
	resp.Diagnostics.Append(resp.State.Set(ctx, &plan)...)
}

func (r *FlowResource) Read(ctx context.Context, req resource.ReadRequest, resp *resource.ReadResponse) {
	var state FlowResourceModel
	resp.Diagnostics.Append(req.State.Get(ctx, &state)...)
	if resp.Diagnostics.HasError() {
		return
	}

	flow, err := r.client.GetFlow(ctx, state.ID.ValueString())
	if err != nil {
		resp.Diagnostics.AddError("Error reading flow", err.Error())
		return
	}
	if flow == nil {
		resp.State.RemoveResource(ctx)
		return
	}

	resp.Diagnostics.Append(setFlowState(&state, flow)...)
	resp.Diagnostics.Append(resp.State.Set(ctx, &state)...)
}

func (r *FlowResource) Update(ctx context.Context, req resource.UpdateRequest, resp *resource.UpdateResponse) {
	var plan FlowResourceModel
	resp.Diagnostics.Append(req.Plan.Get(ctx, &plan)...)
	if resp.Diagnostics.HasError() {
		return
	}

	attestationTypes := listToStringSlice(ctx, plan.RequiredAttestationTypes, &resp.Diagnostics)
	if resp.Diagnostics.HasError() {
		return
	}

	name := plan.Name.ValueString()
	desc := plan.Description.ValueString()
	flow, err := r.client.UpdateFlow(ctx, plan.ID.ValueString(), FlowUpdateRequest{
		Name:                    &name,
		Description:             &desc,
		RequiredAttestationTypes: attestationTypes,
	})
	if err != nil {
		resp.Diagnostics.AddError("Error updating flow", err.Error())
		return
	}

	resp.Diagnostics.Append(setFlowState(&plan, flow)...)
	resp.Diagnostics.Append(resp.State.Set(ctx, &plan)...)
}

func (r *FlowResource) Delete(ctx context.Context, req resource.DeleteRequest, resp *resource.DeleteResponse) {
	var state FlowResourceModel
	resp.Diagnostics.Append(req.State.Get(ctx, &state)...)
	if resp.Diagnostics.HasError() {
		return
	}

	if err := r.client.DeleteFlow(ctx, state.ID.ValueString()); err != nil {
		resp.Diagnostics.AddError("Error deleting flow", err.Error())
	}
}

func (r *FlowResource) ImportState(ctx context.Context, req resource.ImportStateRequest, resp *resource.ImportStateResponse) {
	flow, err := r.client.GetFlow(ctx, req.ID)
	if err != nil {
		resp.Diagnostics.AddError("Error importing flow", err.Error())
		return
	}
	if flow == nil {
		resp.Diagnostics.AddError("Flow not found", fmt.Sprintf("No flow found with ID: %s", req.ID))
		return
	}

	var state FlowResourceModel
	resp.Diagnostics.Append(setFlowState(&state, flow)...)
	resp.Diagnostics.Append(resp.State.Set(ctx, &state)...)
}

func setFlowState(state *FlowResourceModel, flow *FlowResponse) diag.Diagnostics {
	state.ID = types.StringValue(flow.ID)
	state.Name = types.StringValue(flow.Name)
	state.Description = types.StringValue(flow.Description)
	state.CreatedAt = types.StringValue(flow.CreatedAt)
	state.UpdatedAt = types.StringValue(flow.UpdatedAt)

	attrVals := make([]attr.Value, len(flow.RequiredAttestationTypes))
	for i, t := range flow.RequiredAttestationTypes {
		attrVals[i] = types.StringValue(t)
	}
	listVal, diags := types.ListValue(types.StringType, attrVals)
	state.RequiredAttestationTypes = listVal
	return diags
}

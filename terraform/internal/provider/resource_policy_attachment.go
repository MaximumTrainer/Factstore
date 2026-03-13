package provider

import (
	"context"
	"fmt"

	"github.com/hashicorp/terraform-plugin-framework/resource"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema/planmodifier"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema/stringplanmodifier"
	"github.com/hashicorp/terraform-plugin-framework/types"
)

var _ resource.Resource = &PolicyAttachmentResource{}
var _ resource.ResourceWithImportState = &PolicyAttachmentResource{}

func NewPolicyAttachmentResource() resource.Resource {
	return &PolicyAttachmentResource{}
}

type PolicyAttachmentResource struct {
	client *Client
}

type PolicyAttachmentResourceModel struct {
	ID            types.String `tfsdk:"id"`
	PolicyID      types.String `tfsdk:"policy_id"`
	EnvironmentID types.String `tfsdk:"environment_id"`
	CreatedAt     types.String `tfsdk:"created_at"`
}

func (r *PolicyAttachmentResource) Metadata(_ context.Context, req resource.MetadataRequest, resp *resource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_policy_attachment"
}

func (r *PolicyAttachmentResource) Schema(_ context.Context, _ resource.SchemaRequest, resp *resource.SchemaResponse) {
	resp.Schema = schema.Schema{
		MarkdownDescription: "Attaches a Factstore Policy to an Environment.",
		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The unique identifier of the policy attachment.",
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"policy_id": schema.StringAttribute{
				Required:            true,
				MarkdownDescription: "The ID of the policy to attach.",
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.RequiresReplace(),
				},
			},
			"environment_id": schema.StringAttribute{
				Required:            true,
				MarkdownDescription: "The ID of the environment to attach the policy to.",
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.RequiresReplace(),
				},
			},
			"created_at": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The timestamp when the attachment was created.",
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
		},
	}
}

func (r *PolicyAttachmentResource) Configure(_ context.Context, req resource.ConfigureRequest, resp *resource.ConfigureResponse) {
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

func (r *PolicyAttachmentResource) Create(ctx context.Context, req resource.CreateRequest, resp *resource.CreateResponse) {
	var plan PolicyAttachmentResourceModel
	resp.Diagnostics.Append(req.Plan.Get(ctx, &plan)...)
	if resp.Diagnostics.HasError() {
		return
	}

	attachment, err := r.client.CreatePolicyAttachment(ctx, PolicyAttachmentRequest{
		PolicyID:      plan.PolicyID.ValueString(),
		EnvironmentID: plan.EnvironmentID.ValueString(),
	})
	if err != nil {
		resp.Diagnostics.AddError("Error creating policy attachment", err.Error())
		return
	}

	setPolicyAttachmentState(&plan, attachment)
	resp.Diagnostics.Append(resp.State.Set(ctx, &plan)...)
}

func (r *PolicyAttachmentResource) Read(ctx context.Context, req resource.ReadRequest, resp *resource.ReadResponse) {
	var state PolicyAttachmentResourceModel
	resp.Diagnostics.Append(req.State.Get(ctx, &state)...)
	if resp.Diagnostics.HasError() {
		return
	}

	attachment, err := r.client.GetPolicyAttachment(ctx, state.ID.ValueString())
	if err != nil {
		resp.Diagnostics.AddError("Error reading policy attachment", err.Error())
		return
	}
	if attachment == nil {
		resp.State.RemoveResource(ctx)
		return
	}

	setPolicyAttachmentState(&state, attachment)
	resp.Diagnostics.Append(resp.State.Set(ctx, &state)...)
}

func (r *PolicyAttachmentResource) Update(_ context.Context, _ resource.UpdateRequest, _ *resource.UpdateResponse) {
	// Policy attachments have no updatable fields — all fields use RequiresReplace.
}

func (r *PolicyAttachmentResource) Delete(ctx context.Context, req resource.DeleteRequest, resp *resource.DeleteResponse) {
	var state PolicyAttachmentResourceModel
	resp.Diagnostics.Append(req.State.Get(ctx, &state)...)
	if resp.Diagnostics.HasError() {
		return
	}

	if err := r.client.DeletePolicyAttachment(ctx, state.ID.ValueString()); err != nil {
		resp.Diagnostics.AddError("Error deleting policy attachment", err.Error())
	}
}

func (r *PolicyAttachmentResource) ImportState(ctx context.Context, req resource.ImportStateRequest, resp *resource.ImportStateResponse) {
	attachment, err := r.client.GetPolicyAttachment(ctx, req.ID)
	if err != nil {
		resp.Diagnostics.AddError("Error importing policy attachment", err.Error())
		return
	}
	if attachment == nil {
		resp.Diagnostics.AddError("Policy attachment not found", fmt.Sprintf("No policy attachment found with ID: %s", req.ID))
		return
	}

	var state PolicyAttachmentResourceModel
	setPolicyAttachmentState(&state, attachment)
	resp.Diagnostics.Append(resp.State.Set(ctx, &state)...)
}

func setPolicyAttachmentState(state *PolicyAttachmentResourceModel, attachment *PolicyAttachmentResponse) {
	state.ID = types.StringValue(attachment.ID)
	state.PolicyID = types.StringValue(attachment.PolicyID)
	state.EnvironmentID = types.StringValue(attachment.EnvironmentID)
	state.CreatedAt = types.StringValue(attachment.CreatedAt)
}

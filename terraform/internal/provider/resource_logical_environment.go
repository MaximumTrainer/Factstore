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

var _ resource.Resource = &LogicalEnvironmentResource{}
var _ resource.ResourceWithImportState = &LogicalEnvironmentResource{}

func NewLogicalEnvironmentResource() resource.Resource {
	return &LogicalEnvironmentResource{}
}

type LogicalEnvironmentResource struct {
	client *Client
}

type LogicalEnvironmentResourceModel struct {
	ID          types.String `tfsdk:"id"`
	Name        types.String `tfsdk:"name"`
	Description types.String `tfsdk:"description"`
	CreatedAt   types.String `tfsdk:"created_at"`
	UpdatedAt   types.String `tfsdk:"updated_at"`
}

func (r *LogicalEnvironmentResource) Metadata(_ context.Context, req resource.MetadataRequest, resp *resource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_logical_environment"
}

func (r *LogicalEnvironmentResource) Schema(_ context.Context, _ resource.SchemaRequest, resp *resource.SchemaResponse) {
	resp.Schema = schema.Schema{
		MarkdownDescription: "Manages a Factstore Logical Environment. Logical Environments group physical environments for policy enforcement.",
		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The unique identifier of the logical environment.",
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"name": schema.StringAttribute{
				Required:            true,
				MarkdownDescription: "The unique name of the logical environment.",
			},
			"description": schema.StringAttribute{
				Optional:            true,
				Computed:            true,
				MarkdownDescription: "A human-readable description of the logical environment.",
			},
			"created_at": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The timestamp when the logical environment was created.",
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"updated_at": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The timestamp when the logical environment was last updated.",
			},
		},
	}
}

func (r *LogicalEnvironmentResource) Configure(_ context.Context, req resource.ConfigureRequest, resp *resource.ConfigureResponse) {
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

func (r *LogicalEnvironmentResource) Create(ctx context.Context, req resource.CreateRequest, resp *resource.CreateResponse) {
	var plan LogicalEnvironmentResourceModel
	resp.Diagnostics.Append(req.Plan.Get(ctx, &plan)...)
	if resp.Diagnostics.HasError() {
		return
	}

	env, err := r.client.CreateLogicalEnvironment(ctx, LogicalEnvironmentRequest{
		Name:        plan.Name.ValueString(),
		Description: plan.Description.ValueString(),
	})
	if err != nil {
		resp.Diagnostics.AddError("Error creating logical environment", err.Error())
		return
	}

	setLogicalEnvironmentState(&plan, env)
	resp.Diagnostics.Append(resp.State.Set(ctx, &plan)...)
}

func (r *LogicalEnvironmentResource) Read(ctx context.Context, req resource.ReadRequest, resp *resource.ReadResponse) {
	var state LogicalEnvironmentResourceModel
	resp.Diagnostics.Append(req.State.Get(ctx, &state)...)
	if resp.Diagnostics.HasError() {
		return
	}

	env, err := r.client.GetLogicalEnvironment(ctx, state.ID.ValueString())
	if err != nil {
		resp.Diagnostics.AddError("Error reading logical environment", err.Error())
		return
	}
	if env == nil {
		resp.State.RemoveResource(ctx)
		return
	}

	setLogicalEnvironmentState(&state, env)
	resp.Diagnostics.Append(resp.State.Set(ctx, &state)...)
}

func (r *LogicalEnvironmentResource) Update(ctx context.Context, req resource.UpdateRequest, resp *resource.UpdateResponse) {
	var plan LogicalEnvironmentResourceModel
	resp.Diagnostics.Append(req.Plan.Get(ctx, &plan)...)
	if resp.Diagnostics.HasError() {
		return
	}

	name := plan.Name.ValueString()
	desc := plan.Description.ValueString()
	env, err := r.client.UpdateLogicalEnvironment(ctx, plan.ID.ValueString(), LogicalEnvironmentUpdateRequest{
		Name:        &name,
		Description: &desc,
	})
	if err != nil {
		resp.Diagnostics.AddError("Error updating logical environment", err.Error())
		return
	}

	setLogicalEnvironmentState(&plan, env)
	resp.Diagnostics.Append(resp.State.Set(ctx, &plan)...)
}

func (r *LogicalEnvironmentResource) Delete(ctx context.Context, req resource.DeleteRequest, resp *resource.DeleteResponse) {
	var state LogicalEnvironmentResourceModel
	resp.Diagnostics.Append(req.State.Get(ctx, &state)...)
	if resp.Diagnostics.HasError() {
		return
	}

	if err := r.client.DeleteLogicalEnvironment(ctx, state.ID.ValueString()); err != nil {
		resp.Diagnostics.AddError("Error deleting logical environment", err.Error())
	}
}

func (r *LogicalEnvironmentResource) ImportState(ctx context.Context, req resource.ImportStateRequest, resp *resource.ImportStateResponse) {
	env, err := r.client.GetLogicalEnvironment(ctx, req.ID)
	if err != nil {
		resp.Diagnostics.AddError("Error importing logical environment", err.Error())
		return
	}
	if env == nil {
		resp.Diagnostics.AddError("Logical environment not found", fmt.Sprintf("No logical environment found with ID: %s", req.ID))
		return
	}

	var state LogicalEnvironmentResourceModel
	setLogicalEnvironmentState(&state, env)
	resp.Diagnostics.Append(resp.State.Set(ctx, &state)...)
}

func setLogicalEnvironmentState(state *LogicalEnvironmentResourceModel, env *LogicalEnvironmentResponse) {
	state.ID = types.StringValue(env.ID)
	state.Name = types.StringValue(env.Name)
	state.Description = types.StringValue(env.Description)
	state.CreatedAt = types.StringValue(env.CreatedAt)
	state.UpdatedAt = types.StringValue(env.UpdatedAt)
}

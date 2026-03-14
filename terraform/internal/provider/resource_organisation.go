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

var _ resource.Resource = &OrganisationResource{}
var _ resource.ResourceWithImportState = &OrganisationResource{}

func NewOrganisationResource() resource.Resource {
	return &OrganisationResource{}
}

type OrganisationResource struct {
	client *Client
}

type OrganisationResourceModel struct {
	ID          types.String `tfsdk:"id"`
	Slug        types.String `tfsdk:"slug"`
	Name        types.String `tfsdk:"name"`
	Description types.String `tfsdk:"description"`
	CreatedAt   types.String `tfsdk:"created_at"`
	UpdatedAt   types.String `tfsdk:"updated_at"`
}

func (r *OrganisationResource) Metadata(_ context.Context, req resource.MetadataRequest, resp *resource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_organisation"
}

func (r *OrganisationResource) Schema(_ context.Context, _ resource.SchemaRequest, resp *resource.SchemaResponse) {
	resp.Schema = schema.Schema{
		MarkdownDescription: "Manages a Factstore Organisation. Organisations are the top-level grouping for teams and members.",
		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The unique identifier of the organisation.",
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"slug": schema.StringAttribute{
				Required:            true,
				MarkdownDescription: "The unique URL-friendly slug for the organisation.",
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.RequiresReplace(),
				},
			},
			"name": schema.StringAttribute{
				Required:            true,
				MarkdownDescription: "The display name of the organisation.",
			},
			"description": schema.StringAttribute{
				Optional:            true,
				Computed:            true,
				MarkdownDescription: "A human-readable description of the organisation.",
			},
			"created_at": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The timestamp when the organisation was created.",
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"updated_at": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The timestamp when the organisation was last updated.",
			},
		},
	}
}

func (r *OrganisationResource) Configure(_ context.Context, req resource.ConfigureRequest, resp *resource.ConfigureResponse) {
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

func (r *OrganisationResource) Create(ctx context.Context, req resource.CreateRequest, resp *resource.CreateResponse) {
	var plan OrganisationResourceModel
	resp.Diagnostics.Append(req.Plan.Get(ctx, &plan)...)
	if resp.Diagnostics.HasError() {
		return
	}

	org, err := r.client.CreateOrganisation(ctx, OrganisationRequest{
		Slug:        plan.Slug.ValueString(),
		Name:        plan.Name.ValueString(),
		Description: plan.Description.ValueString(),
	})
	if err != nil {
		resp.Diagnostics.AddError("Error creating organisation", err.Error())
		return
	}

	setOrganisationState(&plan, org)
	resp.Diagnostics.Append(resp.State.Set(ctx, &plan)...)
}

func (r *OrganisationResource) Read(ctx context.Context, req resource.ReadRequest, resp *resource.ReadResponse) {
	var state OrganisationResourceModel
	resp.Diagnostics.Append(req.State.Get(ctx, &state)...)
	if resp.Diagnostics.HasError() {
		return
	}

	org, err := r.client.GetOrganisation(ctx, state.ID.ValueString())
	if err != nil {
		resp.Diagnostics.AddError("Error reading organisation", err.Error())
		return
	}
	if org == nil {
		resp.State.RemoveResource(ctx)
		return
	}

	setOrganisationState(&state, org)
	resp.Diagnostics.Append(resp.State.Set(ctx, &state)...)
}

func (r *OrganisationResource) Update(ctx context.Context, req resource.UpdateRequest, resp *resource.UpdateResponse) {
	var plan OrganisationResourceModel
	resp.Diagnostics.Append(req.Plan.Get(ctx, &plan)...)
	if resp.Diagnostics.HasError() {
		return
	}

	name := plan.Name.ValueString()
	desc := plan.Description.ValueString()
	org, err := r.client.UpdateOrganisation(ctx, plan.ID.ValueString(), OrganisationUpdateRequest{
		Name:        &name,
		Description: &desc,
	})
	if err != nil {
		resp.Diagnostics.AddError("Error updating organisation", err.Error())
		return
	}

	setOrganisationState(&plan, org)
	resp.Diagnostics.Append(resp.State.Set(ctx, &plan)...)
}

func (r *OrganisationResource) Delete(ctx context.Context, req resource.DeleteRequest, resp *resource.DeleteResponse) {
	var state OrganisationResourceModel
	resp.Diagnostics.Append(req.State.Get(ctx, &state)...)
	if resp.Diagnostics.HasError() {
		return
	}

	if err := r.client.DeleteOrganisation(ctx, state.ID.ValueString()); err != nil {
		resp.Diagnostics.AddError("Error deleting organisation", err.Error())
	}
}

func (r *OrganisationResource) ImportState(ctx context.Context, req resource.ImportStateRequest, resp *resource.ImportStateResponse) {
	org, err := r.client.GetOrganisation(ctx, req.ID)
	if err != nil {
		resp.Diagnostics.AddError("Error importing organisation", err.Error())
		return
	}
	if org == nil {
		resp.Diagnostics.AddError("Organisation not found", fmt.Sprintf("No organisation found with ID: %s", req.ID))
		return
	}

	var state OrganisationResourceModel
	setOrganisationState(&state, org)
	resp.Diagnostics.Append(resp.State.Set(ctx, &state)...)
}

func setOrganisationState(state *OrganisationResourceModel, org *OrganisationResponse) {
	state.ID = types.StringValue(org.ID)
	state.Slug = types.StringValue(org.Slug)
	state.Name = types.StringValue(org.Name)
	state.Description = types.StringValue(org.Description)
	state.CreatedAt = types.StringValue(org.CreatedAt)
	state.UpdatedAt = types.StringValue(org.UpdatedAt)
}

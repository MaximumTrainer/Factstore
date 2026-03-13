package provider

import (
	"context"
	"fmt"

	"github.com/hashicorp/terraform-plugin-framework/datasource"
	"github.com/hashicorp/terraform-plugin-framework/datasource/schema"
	"github.com/hashicorp/terraform-plugin-framework/types"
)

var _ datasource.DataSource = &EnvironmentDataSource{}

func NewEnvironmentDataSource() datasource.DataSource {
	return &EnvironmentDataSource{}
}

type EnvironmentDataSource struct {
	client *Client
}

type EnvironmentDataSourceModel struct {
	ID          types.String `tfsdk:"id"`
	Name        types.String `tfsdk:"name"`
	Type        types.String `tfsdk:"type"`
	Description types.String `tfsdk:"description"`
	CreatedAt   types.String `tfsdk:"created_at"`
	UpdatedAt   types.String `tfsdk:"updated_at"`
}

func (d *EnvironmentDataSource) Metadata(_ context.Context, req datasource.MetadataRequest, resp *datasource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_environment"
}

func (d *EnvironmentDataSource) Schema(_ context.Context, _ datasource.SchemaRequest, resp *datasource.SchemaResponse) {
	resp.Schema = schema.Schema{
		MarkdownDescription: "Reads an existing Factstore Environment by ID.",
		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Required:            true,
				MarkdownDescription: "The unique identifier of the environment.",
			},
			"name": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The name of the environment.",
			},
			"type": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The type of the environment (K8S, ECS, VM, PHYSICAL, SERVERLESS).",
			},
			"description": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The description of the environment.",
			},
			"created_at": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The timestamp when the environment was created.",
			},
			"updated_at": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The timestamp when the environment was last updated.",
			},
		},
	}
}

func (d *EnvironmentDataSource) Configure(_ context.Context, req datasource.ConfigureRequest, resp *datasource.ConfigureResponse) {
	if req.ProviderData == nil {
		return
	}
	client, ok := req.ProviderData.(*Client)
	if !ok {
		resp.Diagnostics.AddError("Unexpected Provider Data Type",
			fmt.Sprintf("Expected *Client, got: %T", req.ProviderData))
		return
	}
	d.client = client
}

func (d *EnvironmentDataSource) Read(ctx context.Context, req datasource.ReadRequest, resp *datasource.ReadResponse) {
	var data EnvironmentDataSourceModel
	resp.Diagnostics.Append(req.Config.Get(ctx, &data)...)
	if resp.Diagnostics.HasError() {
		return
	}

	env, err := d.client.GetEnvironment(ctx, data.ID.ValueString())
	if err != nil {
		resp.Diagnostics.AddError("Error reading environment", err.Error())
		return
	}
	if env == nil {
		resp.Diagnostics.AddError("Environment not found", fmt.Sprintf("No environment found with ID: %s", data.ID.ValueString()))
		return
	}

	data.Name = types.StringValue(env.Name)
	data.Type = types.StringValue(env.Type)
	data.Description = types.StringValue(env.Description)
	data.CreatedAt = types.StringValue(env.CreatedAt)
	data.UpdatedAt = types.StringValue(env.UpdatedAt)

	resp.Diagnostics.Append(resp.State.Set(ctx, &data)...)
}

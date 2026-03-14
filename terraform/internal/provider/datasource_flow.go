package provider

import (
	"context"
	"fmt"

	"github.com/hashicorp/terraform-plugin-framework/attr"
	"github.com/hashicorp/terraform-plugin-framework/datasource"
	"github.com/hashicorp/terraform-plugin-framework/datasource/schema"
	"github.com/hashicorp/terraform-plugin-framework/types"
)

var _ datasource.DataSource = &FlowDataSource{}

func NewFlowDataSource() datasource.DataSource {
	return &FlowDataSource{}
}

type FlowDataSource struct {
	client *Client
}

type FlowDataSourceModel struct {
	ID                      types.String `tfsdk:"id"`
	Name                    types.String `tfsdk:"name"`
	Description             types.String `tfsdk:"description"`
	RequiredAttestationTypes types.List   `tfsdk:"required_attestation_types"`
	CreatedAt               types.String `tfsdk:"created_at"`
	UpdatedAt               types.String `tfsdk:"updated_at"`
}

func (d *FlowDataSource) Metadata(_ context.Context, req datasource.MetadataRequest, resp *datasource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_flow"
}

func (d *FlowDataSource) Schema(_ context.Context, _ datasource.SchemaRequest, resp *datasource.SchemaResponse) {
	resp.Schema = schema.Schema{
		MarkdownDescription: "Reads an existing Factstore Flow by ID.",
		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Required:            true,
				MarkdownDescription: "The unique identifier of the flow.",
			},
			"name": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The name of the flow.",
			},
			"description": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The description of the flow.",
			},
			"required_attestation_types": schema.ListAttribute{
				Computed:            true,
				ElementType:         types.StringType,
				MarkdownDescription: "List of attestation types required for trail compliance.",
			},
			"created_at": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The timestamp when the flow was created.",
			},
			"updated_at": schema.StringAttribute{
				Computed:            true,
				MarkdownDescription: "The timestamp when the flow was last updated.",
			},
		},
	}
}

func (d *FlowDataSource) Configure(_ context.Context, req datasource.ConfigureRequest, resp *datasource.ConfigureResponse) {
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

func (d *FlowDataSource) Read(ctx context.Context, req datasource.ReadRequest, resp *datasource.ReadResponse) {
	var data FlowDataSourceModel
	resp.Diagnostics.Append(req.Config.Get(ctx, &data)...)
	if resp.Diagnostics.HasError() {
		return
	}

	flow, err := d.client.GetFlow(ctx, data.ID.ValueString())
	if err != nil {
		resp.Diagnostics.AddError("Error reading flow", err.Error())
		return
	}
	if flow == nil {
		resp.Diagnostics.AddError("Flow not found", fmt.Sprintf("No flow found with ID: %s", data.ID.ValueString()))
		return
	}

	data.Name = types.StringValue(flow.Name)
	data.Description = types.StringValue(flow.Description)
	data.CreatedAt = types.StringValue(flow.CreatedAt)
	data.UpdatedAt = types.StringValue(flow.UpdatedAt)

	attrVals := make([]attr.Value, len(flow.RequiredAttestationTypes))
	for i, t := range flow.RequiredAttestationTypes {
		attrVals[i] = types.StringValue(t)
	}
	listVal, diags := types.ListValue(types.StringType, attrVals)
	resp.Diagnostics.Append(diags...)
	data.RequiredAttestationTypes = listVal

	resp.Diagnostics.Append(resp.State.Set(ctx, &data)...)
}

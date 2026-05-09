package provider

import (
	"context"

	"github.com/hashicorp/terraform-plugin-framework/datasource"
	"github.com/hashicorp/terraform-plugin-framework/provider"
	"github.com/hashicorp/terraform-plugin-framework/provider/schema"
	"github.com/hashicorp/terraform-plugin-framework/resource"
	"github.com/hashicorp/terraform-plugin-framework/types"
)

var _ provider.Provider = &FactstoreProvider{}

type FactstoreProvider struct {
	version string
}

type FactstoreProviderModel struct {
	Endpoint types.String `tfsdk:"endpoint"`
}

func New(version string) func() provider.Provider {
	return func() provider.Provider {
		return &FactstoreProvider{version: version}
	}
}

func (p *FactstoreProvider) Metadata(_ context.Context, _ provider.MetadataRequest, resp *provider.MetadataResponse) {
	resp.TypeName = "factstore"
	resp.Version = p.version
}

func (p *FactstoreProvider) Schema(_ context.Context, _ provider.SchemaRequest, resp *provider.SchemaResponse) {
	resp.Schema = schema.Schema{
		Description: "Provider for managing OpenFactstore resources.",
		Attributes: map[string]schema.Attribute{
			"endpoint": schema.StringAttribute{
				Optional:    true,
				Description: "Base URL for the Factstore API. Defaults to http://localhost:8080.",
			},
		},
	}
}

func (p *FactstoreProvider) Configure(ctx context.Context, req provider.ConfigureRequest, resp *provider.ConfigureResponse) {
	var config FactstoreProviderModel
	diags := req.Config.Get(ctx, &config)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	endpoint := "http://localhost:8080"
	if !config.Endpoint.IsNull() && !config.Endpoint.IsUnknown() && config.Endpoint.ValueString() != "" {
		endpoint = config.Endpoint.ValueString()
	}

	resp.DataSourceData = endpoint
	resp.ResourceData = endpoint
}

func (p *FactstoreProvider) Resources(_ context.Context) []func() resource.Resource {
	return []func() resource.Resource{
		NewFlowResource,
		NewEnvironmentResource,
	}
}

func (p *FactstoreProvider) DataSources(_ context.Context) []func() datasource.DataSource {
	return nil
}

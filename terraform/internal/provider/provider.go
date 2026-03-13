package provider

import (
	"context"
	"os"

	"github.com/hashicorp/terraform-plugin-framework/datasource"
	"github.com/hashicorp/terraform-plugin-framework/path"
	"github.com/hashicorp/terraform-plugin-framework/provider"
	"github.com/hashicorp/terraform-plugin-framework/provider/schema"
	"github.com/hashicorp/terraform-plugin-framework/resource"
	"github.com/hashicorp/terraform-plugin-framework/types"
)

// Ensure FactstoreProvider satisfies various provider interfaces.
var _ provider.Provider = &FactstoreProvider{}

// FactstoreProvider defines the provider implementation.
type FactstoreProvider struct {
	version string
}

// FactstoreProviderModel describes the provider data model.
type FactstoreProviderModel struct {
	BaseURL  types.String `tfsdk:"base_url"`
	APIToken types.String `tfsdk:"api_token"`
}

// New returns a new provider factory function.
func New(version string) func() provider.Provider {
	return func() provider.Provider {
		return &FactstoreProvider{
			version: version,
		}
	}
}

func (p *FactstoreProvider) Metadata(_ context.Context, _ provider.MetadataRequest, resp *provider.MetadataResponse) {
	resp.TypeName = "factstore"
	resp.Version = p.version
}

func (p *FactstoreProvider) Schema(_ context.Context, _ provider.SchemaRequest, resp *provider.SchemaResponse) {
	resp.Schema = schema.Schema{
		MarkdownDescription: "The Factstore provider manages Factstore resources as Infrastructure-as-Code.",
		Attributes: map[string]schema.Attribute{
			"base_url": schema.StringAttribute{
				MarkdownDescription: "Base URL of the Factstore API (e.g. `https://factstore.example.com`). Can also be set with the `FACTSTORE_BASE_URL` environment variable.",
				Optional:            true,
			},
			"api_token": schema.StringAttribute{
				MarkdownDescription: "API token for authentication. Can also be set with the `FACTSTORE_API_TOKEN` environment variable.",
				Optional:            true,
				Sensitive:           true,
			},
		},
	}
}

func (p *FactstoreProvider) Configure(ctx context.Context, req provider.ConfigureRequest, resp *provider.ConfigureResponse) {
	var config FactstoreProviderModel
	resp.Diagnostics.Append(req.Config.Get(ctx, &config)...)
	if resp.Diagnostics.HasError() {
		return
	}

	baseURL := os.Getenv("FACTSTORE_BASE_URL")
	if !config.BaseURL.IsNull() {
		baseURL = config.BaseURL.ValueString()
	}

	apiToken := os.Getenv("FACTSTORE_API_TOKEN")
	if !config.APIToken.IsNull() {
		apiToken = config.APIToken.ValueString()
	}

	if baseURL == "" {
		resp.Diagnostics.AddAttributeError(
			path.Root("base_url"),
			"Missing Factstore Base URL",
			"The provider requires a base_url configuration value or the FACTSTORE_BASE_URL environment variable.",
		)
	}

	if resp.Diagnostics.HasError() {
		return
	}

	client := NewClient(baseURL, apiToken)
	resp.DataSourceData = client
	resp.ResourceData = client
}

func (p *FactstoreProvider) Resources(_ context.Context) []func() resource.Resource {
	return []func() resource.Resource{
		NewFlowResource,
		NewEnvironmentResource,
		NewPolicyResource,
		NewPolicyAttachmentResource,
		NewLogicalEnvironmentResource,
		NewOrganisationResource,
	}
}

func (p *FactstoreProvider) DataSources(_ context.Context) []func() datasource.DataSource {
	return []func() datasource.DataSource{
		NewFlowDataSource,
		NewEnvironmentDataSource,
	}
}

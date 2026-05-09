package main

import (
	"context"
	"flag"
	"log"

	"github.com/hashicorp/terraform-plugin-framework/providerserver"

	"github.com/MaximumTrainer/openfactstore/terraform/provider/internal/provider"
)

func main() {
	tfmain()
}

func tfmain() {
	var debug bool
	flag.BoolVar(&debug, "debug", false, "set to true to run the provider with support for debuggers")
	flag.Parse()

	opts := providerserver.ServeOpts{
		Address: "registry.terraform.io/MaximumTrainer/factstore",
		Debug:   debug,
	}

	err := providerserver.Serve(context.Background(), provider.New("dev"), opts)
	if err != nil {
		log.Fatal(err)
	}
}

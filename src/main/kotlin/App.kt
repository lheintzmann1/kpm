package kpm

import core.Cli

fun main(args: Array<String>) {
    // Initialize the CLI application
    val cli = Cli()

    // Run the CLI with the provided arguments
    cli.run(args)
}
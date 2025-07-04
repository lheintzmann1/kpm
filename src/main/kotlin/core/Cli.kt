package core

import kpm.utils.*

class Cli {
    fun run(args: Array<String>) {
        if (args.isEmpty()) {
            Help().help()
            return
        }

        when (args[0]) {
            "install" -> {
                if (args.size < 2) {
                    println("Error: Package name is required for install command.")
                } else {
                    println("Installing package: ${args[1]}")
                    // Logic to install the package
                }
            }
            "remove" -> {
                if (args.size < 2) {
                    println("Error: Package name is required for remove command.")
                } else {
                    println("Removing package: ${args[1]}")
                    // Logic to remove the package
                }
            }
            "list" -> {
                println("Listing installed packages...")
                // Logic to list installed packages
            }
            "update" -> {
                println("Updating all packages...")
                // Logic to update packages
            }
            "help" -> Help().help()
            else -> println("Unknown command: ${args[0]}. Use 'help' for available commands.")
        }
    }
}
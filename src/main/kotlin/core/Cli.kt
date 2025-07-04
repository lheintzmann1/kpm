/*
Copyright 2025 Lucas HEINTZMANN

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

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
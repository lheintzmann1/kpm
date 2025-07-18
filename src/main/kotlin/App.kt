﻿/*
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

package kpm

import com.github.ajalt.clikt.core.*
import kpm.commands.*
import kpm.utils.Logger
import kotlin.system.exitProcess

class Kpm : CliktCommand() {
    override fun run() { }
}

fun main(args: Array<String>) {
    try {
        Kpm()
            .subcommands(
                Init(),
                Install(),
                Build(),
                Gc(),
                Version()
            )
            .main(args)
    } catch (e: Exception) {
        Logger.error("An unexpected error occurred: ${e.message}", e)
        exitProcess(1)
    }
}
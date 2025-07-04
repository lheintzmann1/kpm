package kpm.utils

class Help {
    fun help() {
        println("Usage: kpm [command]")
        println("Commands:")
        println("  install <package>   Install a package")
        println("  remove <package>    Remove a package")
        println("  list                List installed packages")
        println("  update              Update all packages")
        println("  help                Show this help message")
        println("Options:")
        println("  --version           Show version information")
        println("  --verbose           Enable verbose output")
    }
}
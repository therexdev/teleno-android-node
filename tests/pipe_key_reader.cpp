#include <filesystem>
#include <fstream>
#include <string>

// Mirrors only the upstream file-opening mechanism, using a public fixture.
// It demonstrates inherited-pipe transport on host Linux, not Android policy.
int main() {
    const std::filesystem::path path("/proc/self/fd/0");
    if (!std::filesystem::exists(path)) return 2;
    std::ifstream input(path);
    std::string value;
    if (!input.is_open() || !std::getline(input, value)) return 3;
    return value == "public-fixture-only" ? 0 : 4;
}

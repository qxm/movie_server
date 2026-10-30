param(
    [Parameter(Mandatory = $true)][string]$SourceDirectory,
    [string]$DestinationDirectory = (Join-Path $PSScriptRoot '../uploads/artworks')
)
$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $SourceDirectory).Path
$destination = [IO.Path]::GetFullPath($DestinationDirectory)
if ($source -eq $destination) { throw 'Source and destination must differ.' }
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$copied = 0
foreach ($file in Get-ChildItem -LiteralPath $source -File) {
    if ($file.Name -notmatch '^[a-f0-9-]{36}\.(jpg|png|webp)$') { continue }
    if ($file.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Refusing symbolic link: $($file.Name)" }
    $target = Join-Path $destination $file.Name
    $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
    if (Test-Path -LiteralPath $target) {
        if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $hash) {
            throw "Conflicting destination file: $($file.Name)"
        }
        continue
    }
    Copy-Item -LiteralPath $file.FullName -Destination $target
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $hash) { throw 'Copy verification failed.' }
    $copied++
}
Write-Output "Copied $copied artwork files. Original files were retained."

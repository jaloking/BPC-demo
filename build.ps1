param(
    [string]$CplexHome = $env:CPLEX_STUDIO_DIR201,
    [switch]$Run
)
$ErrorActionPreference = 'Stop'
if (-not $CplexHome) {
    $candidates = @('D:\develop tools\CPlEX 20.1',
        'C:\Program Files\IBM\ILOG\CPLEX_Studio201',
        'C:\Program Files\IBM\ILOG\CPLEX_Studio_Community201')
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath (Join-Path $candidate 'cplex\lib\cplex.jar')) {
            $CplexHome = $candidate
            break
        }
    }
}
if (-not $CplexHome) { throw 'Set CPLEX_STUDIO_DIR201 or pass -CplexHome.' }
$jar = Join-Path $CplexHome 'cplex\lib\cplex.jar'
$native = Join-Path $CplexHome 'cplex\bin\x64_win64'
if (-not (Test-Path -LiteralPath $jar)) { throw "CPLEX jar not found: $jar" }
Write-Output "Using CPLEX: $CplexHome"
$compiler = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\javac.exe' } else { 'javac' }
$runtime = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$output = Join-Path $PSScriptRoot '.build\classes'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$sources = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' |
    ForEach-Object { $_.FullName }
& $compiler -encoding UTF-8 -cp $jar -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
Write-Output 'Compilation succeeded.'
if ($Run) {
    Push-Location $PSScriptRoot
    try {
        & $runtime --enable-native-access=ALL-UNNAMED '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' "-Djava.library.path=$native" -cp "$output;$jar" 'Test.Main'
        if ($LASTEXITCODE -ne 0) { throw 'Java execution failed.' }
    } finally { Pop-Location }
}

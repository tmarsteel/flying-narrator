using System.CommandLine;
using System.CommandLine.Parsing;

namespace NefsEditCLI;

public static class CLIUtils
{
    extension(CommandResult commandResult)
    {
        public bool HasOption(Option option) => commandResult.Children.OfType<OptionResult>().Any(o => o.Option == option);        
    }

    extension(ParseResult parseResult)
    {
        public bool HasFlag(string name) => parseResult.GetResult(name) != null;
    }
}
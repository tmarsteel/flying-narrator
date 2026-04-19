using System.CommandLine;
using System.Diagnostics;
using System.IO.Abstractions;
using System.Runtime.CompilerServices;
using System.Text.Json;
using System.Text.Json.Serialization;
using NefsEditCLI.Interface;
using SharpGLTF.Runtime;
using VictorBush.Ego.NefsLib;
using VictorBush.Ego.NefsLib.IO;
using VictorBush.Ego.NefsLib.Item;
using VictorBush.Ego.NefsLib.Progress;

namespace NefsEditCLI
{
    internal class Program
    {
        private static FileSystem fileSystem = new();
        private static JsonSerializerOptions commandJsonOptions = new()
        {
            AllowOutOfOrderMetadataProperties = true,
            WriteIndented = false,
            TypeInfoResolver = CommandJsonContext.Default
        };

        static async Task<int> Main(string[] args)
        {
            Option<FileInfo> fileOption = new("--file")
            {
                Description = "Path to an NeFS file to read directly off disk"
            };
            var rootCommand = new RootCommand("inspect and extract data from NeFS archives");
            rootCommand.Add(fileOption);

            var execCommandCommand = new Command("commands-from-stdin");
            rootCommand.Add(execCommandCommand);

            var cliParseResult = rootCommand.Parse(args);
            if (cliParseResult.Errors.Count > 0)
            {
                foreach (var parseError in cliParseResult.Errors)
                {
                    Console.Error.WriteLine(parseError.Message);
                }
                
                Console.Error.WriteLine("Usage: nefsedit-cli --file <path-to-nefs-file-on-disk> \"commands-from-stdin\"");
                Console.Error.WriteLine();
                Console.Error.WriteLine("Then send the command as JSON to STDIN, separated by newline (no newlines within one command)");
                
                return 1;
            }

            var nefsPath = cliParseResult.GetRequiredValue(fileOption).FullName;
            
            var reader = new NefsReader(fileSystem);
            NefsArchive archive;
            try
            {
                archive = await reader.ReadArchiveAsync(nefsPath, new NefsProgress());
            }
            catch (FileNotFoundException)
            {
                Console.Error.WriteLine("Could not find file " + nefsPath);
                return 2;
            }
            catch (Exception e)
            {
                Console.Error.WriteLine("Error opening archive " + nefsPath);
                Console.Error.Write(e);
                return 3;
            }
            
            NefsCommand? command;
            while (true)
            {
                try
                {
                    var nextCommandString = Console.ReadLine();
                    if (nextCommandString == null)
                    {
                        break;
                    }
                    command = JsonSerializer.Deserialize<NefsCommand>(nextCommandString, commandJsonOptions);
                }
                catch (Exception ex)
                {
                    Console.Error.WriteLine("Error deserializing command:");
                    Console.Error.WriteLine(ex.Message);
                    return 4;
                }

                if (command == null)
                {
                    Console.Error.WriteLine("Error deserializing command");
                    return 4;
                }
                
                if (command is NefsCommand.Extract.Exit)
                {
                    Console.WriteLine("{}");
                    break;
                }

                var commandResult = await (RunCommand(archive, command));
                if (commandResult != 0)
                {
                    return commandResult;
                }
            }

            return 0;
        }

        private static async Task<int> RunCommand(NefsArchive archive, NefsCommand command)
        {
            if (command is NefsCommand.Enumerate enumerateCmd)
            {
                var responseObject = await Enumerate(archive, enumerateCmd);
                await JsonSerializer.SerializeAsync(Console.OpenStandardOutput(), responseObject,
                    typeof(NefsCommand.Enumerate.Result), commandJsonOptions);
                Console.WriteLine();
                await Console.Out.FlushAsync();
                return 0;
            }

            if (command is NefsCommand.Extract extractCmd)
            {
                var responseObject = await Extract(archive, extractCmd);
                await JsonSerializer.SerializeAsync(Console.OpenStandardOutput(), responseObject,
                    typeof(NefsCommand.Extract.Result), commandJsonOptions);
                Console.WriteLine();
                await Console.Out.FlushAsync();
                return 0;
            }
            
            Console.WriteLine("Unsupported command type " + command.GetType().FullName);
            return 4;
        }

        private static async Task<NefsCommand.Enumerate.Result> Enumerate(NefsArchive archive, NefsCommand.Enumerate command)
        {
            var matchedItems = NefsUtils.DeepEnumerateWithFullPath(archive)
                .Select(itemWithPath => itemWithPath.Key)
                .ToList();
            
            return new NefsCommand.Enumerate.Result(matchedItems);
        }

        private static async Task<NefsCommand.Extract.Result> Extract(NefsArchive archive, NefsCommand.Extract command)
        {
            NefsItem? item = NefsUtils.DeepEnumerateWithFullPath(archive)
                .Where(itemWithPath => itemWithPath.Key == command.pathInNefs)
                .Select(itemWithPath => itemWithPath.Value)
                .SingleOrDefault();

            if (item == null)
            {
                return new NefsCommand.Extract.Result(false, false);
            }

            using(var dataOut = fileSystem.File.OpenWrite(command.extractTo))
            {
                using (var itemRawInStream = item.DataSource.OpenRead(new FileSystem()))
                {
                    try
                    {
                        var itemCleanInStream = command.decodeBinaryXml? WrapBinaryXMLDecode(itemRawInStream) : itemRawInStream;
                        await itemCleanInStream.CopyToAsync(dataOut);
                        dataOut.Close();
                        return new NefsCommand.Extract.Result(true, true);
                    }
                    catch (Exception e)
                    {
                        Console.Error.WriteLine("Error extracting file " + command.pathInNefs + " to " +
                                                command.extractTo);
                        Console.Error.WriteLine(e);
                        return new NefsCommand.Extract.Result(true, false);
                    }
                }
            }
            
        }

        private static Stream WrapBinaryXMLDecode(Stream rawIn)
        {
            // TODO
            return rawIn;
        }
    }
    
    [JsonSourceGenerationOptions(WriteIndented = true)]
    [JsonSerializable(typeof(NefsCommand))]
    [JsonSerializable(typeof(NefsCommand.Enumerate.Result), TypeInfoPropertyName = "EnumerateResult")]
    [JsonSerializable(typeof(NefsCommand.Extract.Result), TypeInfoPropertyName = "ExtractResult")]
    [JsonSerializable(typeof(long))]
    [JsonSerializable(typeof(bool))]
    [JsonSerializable(typeof(string))]
    public partial class CommandJsonContext : JsonSerializerContext { }
}

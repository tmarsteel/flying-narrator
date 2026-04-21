using System.CommandLine;
using System.CommandLine.Help;
using System.IO.Abstractions;
using Google.Protobuf;
using NefsEditCLI.Protocol;
using VictorBush.Ego.NefsLib;
using VictorBush.Ego.NefsLib.IO;
using VictorBush.Ego.NefsLib.Item;
using VictorBush.Ego.NefsLib.Progress;
using VictorBush.Ego.NefsLib.Utility;

namespace NefsEditCLI
{
    internal class Program
    {
        private static FileSystem fileSystem = new();
        private static NefsTransformer nefsTransformer = new NefsTransformer(fileSystem);
        
        private static Option<FileInfo> fileCliOption = new("--file")
        {
            Description = "Path to an NeFS file to read directly off disk",
        };

        private static Option<FileInfo> gameExecutableCliOption = new("--game-executable")
        {
            Description = "The game executable to search for NeFS headers",
            Validators = { IsExistingFileValidator.make() },
        };

        private static Option<FileInfo> dataDirectoryCliOption = new("--data-directory")
        {
            Description = "Directory containing the .dat files as referenced from the game executable",
            Validators = { IsExistingDirectoryValidator.make() },
        };

        private static Option<bool> fullHeaderSearchCliOption = new("--search-entire-executable")
        {
            Description = "If false, NeFS headers will only be searched in the .data/__data sections of the executable",
            DefaultValueFactory = _ => false,
        };

        private static Option<string> dataFilePathCliOption = new("--data-file")
        {
            Description = "Path of the .dat file to load, relative to " + dataDirectoryCliOption.Name,
        };

        static async Task<int> Main(string[] args)
        {
            var rootCommand =
                new RootCommand(
                    "programmatically work with NeFS archives. You can talk to this app like to a server via STDIN/STDOUT using protobuf messages.");
            rootCommand.Add(fileCliOption);
            rootCommand.Add(gameExecutableCliOption);
            rootCommand.Add(dataDirectoryCliOption);
            rootCommand.Add(fullHeaderSearchCliOption);
            rootCommand.Add(dataFilePathCliOption);
            rootCommand.Validators.Add(parseResult =>
            {
                var hasNefsOption = parseResult.HasOption(fileCliOption);
                var hasExeOption = parseResult.HasOption(gameExecutableCliOption);
                var hasDataDirOption = parseResult.HasOption(dataDirectoryCliOption);
                var hasFullHeaderSearchOption = parseResult.HasOption(fullHeaderSearchCliOption);
                var hasDataFileOption = parseResult.HasOption(dataFilePathCliOption);
                if (!hasExeOption && !hasNefsOption)
                {
                    parseResult.AddError("You must specify either " + fileCliOption.Name + " or " + gameExecutableCliOption.Name);
                    return;
                }

                if (hasExeOption && hasNefsOption)
                {
                    parseResult.AddError("You may specify only one of " + fileCliOption.Name + " or " + gameExecutableCliOption.Name);
                    return;
                }

                if (hasExeOption)
                {
                    if (!hasDataDirOption)
                    {
                        parseResult.AddError(gameExecutableCliOption.Name + " requires " + dataDirectoryCliOption.Name);
                    }

                    if (!hasDataFileOption)
                    {
                        parseResult.AddError(gameExecutableCliOption.Name + " requires " + dataFilePathCliOption.Name);
                    }
                }

                if (hasNefsOption)
                {
                    if (hasDataDirOption)
                    {
                        parseResult.AddError(dataDirectoryCliOption.Name + " cannot be used  with " + fileCliOption.Name);
                    }
                    
                    if (hasFullHeaderSearchOption)
                    {
                        parseResult.AddError(fullHeaderSearchCliOption.Name + " cannot be used  with " + fileCliOption.Name);
                    }

                    if (hasDataFileOption)
                    {
                        parseResult.AddError(dataFilePathCliOption.Name + " cannot be used  with " + fileCliOption.Name);
                    }
                }
            });
            
            var cliParseResult = rootCommand.Parse(args);
            if (cliParseResult.GetResult("--help")?.Tokens?.Any() == true)
            {
                return await cliParseResult.InvokeAsync();
            }
            
            var inStream = Console.OpenStandardInput();
            var outStream = Console.OpenStandardOutput();
            
            if (cliParseResult.Errors.Count > 0)
            {
                var response = new FromNefsEdit();
                response.Error = new ErrorResult();
                response.Error.Message = String.Join("\n", cliParseResult.Errors.Select(error => error.Message));
                response.WriteDelimitedTo(outStream);
                await outStream.FlushAsync();
                return 1;
            }

            NefsArchive archive;
            try
            {
                archive = await OpenArchive(cliParseResult);
            }
            catch (FileNotFoundException e)
            {
                var response = new FromNefsEdit();
                response.Error = new ErrorResult();
                response.Error.Message = "Could not find file " + e.FileName;
                response.WriteDelimitedTo(outStream);
                await outStream.FlushAsync();
                return 2;
            }
            catch (Exception e)
            {
                var response = new FromNefsEdit();
                response.Error = new ErrorResult();
                response.Error.Message = "Error opening archive: " + e.GetType().FullName + ": " + e.Message;
                response.WriteDelimitedTo(outStream);
                await outStream.FlushAsync();
                return 3;
            }

            {
                var ackResponse = new FromNefsEdit();
                ackResponse.OpenAck = new FileOpenAcknowledgement();
                ackResponse.OpenAck.Success = true;
                ackResponse.WriteDelimitedTo(outStream);
                await outStream.FlushAsync();
            }
            
            while (true)
            {
                var command = ToNefsEdit.Parser.ParseDelimitedFrom(inStream);
                if (command.Exit != null)
                {
                    break;
                }

                FromNefsEdit response;
                try
                {
                    if (command.ReadItem != null)
                    {
                        response = await ReadItem(archive, command.ReadItem);
                    }
                    else if (command.ListItems != null)
                    {
                        response = await ListItems(archive, command.ListItems);
                    }
                    else
                    {
                        throw new NefsEditCliException("Unknown command");
                    }
                }
                catch (NefsEditCliException e)
                {
                    response = new FromNefsEdit();
                    response.Error = new ErrorResult();
                    response.Error.Message = e.Message;
                }
                
                response.WriteDelimitedTo(outStream);
                await outStream.FlushAsync();
            }
            
            FromNefsEdit goodbye = new FromNefsEdit();
            goodbye.WriteDelimitedTo(outStream);
            await outStream.FlushAsync();

            return 0;
        }

        private static async Task<NefsArchive> OpenArchive(ParseResult cliParseResult)
        {
            var reader = new NefsReader(fileSystem);
            
            var singleFilePath = cliParseResult.GetValue(fileCliOption);
            if (singleFilePath != null)
            {
                return await reader.ReadArchiveAsync(singleFilePath.FullName, new NefsProgress());
            }

            var gameExePath = cliParseResult.GetValue(gameExecutableCliOption);
            if (gameExePath != null)
            {
                var dataDirPath = cliParseResult.GetRequiredValue(dataDirectoryCliOption);
                var fullSearch = cliParseResult.GetRequiredValue(fullHeaderSearchCliOption);
                var relativeDataFilePath = cliParseResult.GetRequiredValue(dataFilePathCliOption);
                var fullDataFilePath = Path.Combine(dataDirPath.FullName, relativeDataFilePath);
                if (!fileSystem.FileInfo.New(fullDataFilePath).Exists)
                {
                    throw new NefsEditCliException("Data file " + fullDataFilePath + " does not exist");
                }
                
                var headlessSources = await new NefsExeHeaderFinder(fileSystem).FindHeadersAsync(
                    gameExePath.FullName,
                    dataDirPath.FullName,
                    fullSearch,
                    new NefsProgress()
                );

                var matchingSources = headlessSources
                    .Where(hs => hs.DataFilePath == fullDataFilePath)
                    .ToList();
                if (!matchingSources.Any())
                {
                    throw new NefsEditCliException("Data file " + relativeDataFilePath + " is not referenced from game executable " + gameExePath.FullName);
                }
                if (matchingSources.Count() > 1)
                {
                    throw new NefsEditCliException("Data file " + relativeDataFilePath + " is ambiguous in game executable " + gameExePath.FullName);
                }

                var source = matchingSources.Single();
                return await reader.ReadGameDatArchiveAsync(
                    fullDataFilePath,
                    gameExePath.FullName,
                    source.PrimaryOffset,
                    source.PrimarySize,
                    source.SecondaryOffset,
                    source.SecondarySize,
                    new NefsProgress()
                );
            }
            
            throw new ArgumentException("Found no source file spec in the command line");
        }
        
        private static async Task<FromNefsEdit> ListItems(NefsArchive archive, ListItemsCommand command)
        {
            IEnumerable<KeyValuePair<String, NefsItem>> itemsToList;
            if (command.HasDirectoryId)
            {
                var directoryId = new NefsItemId(command.DirectoryId);
                var pathToDirectory = NefsUtils.GetPathToItem(archive, directoryId);
                if (command.Recursive)
                {
                    itemsToList = NefsUtils.DeepEnumerateDirectory(archive, pathToDirectory, directoryId);
                }
                else
                {
                    itemsToList = archive.Items.EnumerateItemChildren(directoryId)
                        .Select(child => KeyValuePair.Create(pathToDirectory + "/" + child.FileName, child));
                }
            }
            else
            {
                if (command.Recursive)
                {
                    itemsToList = NefsUtils.DeepEnumerateWithFullPath(archive);
                }
                else
                {
                    itemsToList = archive.Items.EnumerateRootItems()
                        .Select(rootItem => KeyValuePair.Create(NefsUtils.GetPathToItem(archive, rootItem.Id), rootItem));
                }
            }

            var itemsAsProto = itemsToList
                .Select(item =>
                {
                    var protoItem = new ListedItem();
                    protoItem.Id = item.Value.Id.Value;
                    protoItem.FileName = item.Value.FileName;
                    protoItem.FullPath = item.Key;
                    protoItem.Size = item.Value.ExtractedSize;
                    protoItem.IdsOfDuplicates.AddRange(
                        archive.Items.GetItemDuplicates(item.Value.Id)
                            .Select(duplicate => duplicate.Id.Value)
                    );
                    return protoItem;
                });
            
            var responseMessage = new FromNefsEdit();
            responseMessage.ListedItems = new ListItemsResult();
            responseMessage.ListedItems.Items.AddRange(itemsAsProto);
            return responseMessage;
        }

        private static async Task<FromNefsEdit> ReadItem(NefsArchive archive, ReadItemCommand command)
        {
            NefsItem item;
            try
            {
                item = archive.Items.GetItem(new NefsItemId(command.Id));
            }
            catch (KeyNotFoundException)
            {
                throw new NefsEditCliException("Item not found");
            }

            if (item.ExtractedSize > int.MaxValue)
            {
                throw new NefsEditCliException("Item too large, max " + int.MaxValue + " bytes");
            }
            
            using (var itemRawInStream = fileSystem.OpenRead(item.DataSource))
            using (var buffer = new MemoryStream())
            {
                buffer.Capacity = (int) item.ExtractedSize;
                await nefsTransformer.DetransformAsync(
                    itemRawInStream,
                    item.DataSource.Offset,
                    buffer,
                    0,
                    item.ExtractedSize,
                    item.DataSource.Size.Chunks,
                    new NefsProgress()
                );
                buffer.Seek(0, SeekOrigin.Begin);

                FromNefsEdit response = new FromNefsEdit();
                response.Item = new ReadItemResult();
                response.Item.Data = await ByteString.FromStreamAsync(buffer);
                return response;
            }
        }

        private static Stream WrapBinaryXMLDecode(Stream rawIn)
        {
            // TODO
            return rawIn;
        }
    }
    
    internal class NefsEditCliException(string message) : Exception(message) {}
}

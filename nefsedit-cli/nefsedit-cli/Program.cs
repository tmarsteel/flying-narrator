using System.CommandLine;
using System.Diagnostics;
using System.IO.Abstractions;
using System.Runtime.CompilerServices;
using System.Text.Json;
using System.Text.Json.Serialization;
using Google.Protobuf;
using NefsEditCLI.Protocol;
using NefsEditCLI.Interface;
using SharpGLTF.Runtime;
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
        private static NefsTransformer t = new NefsTransformer(fileSystem);
        
        private static Option<FileInfo> fileOption = new("--file")
        {
            Description = "Path to an NeFS file to read directly off disk"
        };

        static async Task<int> Main(string[] args)
        {
            var rootCommand =
                new RootCommand(
                    "programmatically work with NeFS archives. You can talk to this app like to a server via STDIN/STDOUT using protobuf messages.");
            rootCommand.Add(fileOption);
            
            var cliParseResult = rootCommand.Parse(args);
            if (cliParseResult.Errors.Count > 0)
            {
                foreach (var parseError in cliParseResult.Errors)
                {
                    Console.Error.WriteLine(parseError.Message);
                }

                rootCommand.Parse("-h").Invoke();
                
                return 1;
            }

            NefsArchive archive;
            try
            {
                archive = await OpenArchive(cliParseResult);
            }
            catch (FileNotFoundException e)
            {
                Console.Error.WriteLine("Could not find file " + e.FileName);
                return 2;
            }
            catch (Exception e)
            {
                Console.Error.WriteLine("Error opening archive");
                Console.Error.Write(e);
                return 3;
            }

            var inStream = Console.OpenStandardInput();
            var outStream = Console.OpenStandardOutput();
            
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

        private static Task<NefsArchive> OpenArchive(ParseResult cliParseResult)
        {
            var reader = new NefsReader(fileSystem);
            
            var singleFilePath = cliParseResult.GetValue(fileOption);
            if (singleFilePath != null)
            {
                return reader.ReadArchiveAsync(singleFilePath.FullName, new NefsProgress());
            }
            else
            {
                throw new ArgumentException("Found no source file spec in the command line");
            }
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
                await t.DetransformAsync(
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

using VictorBush.Ego.NefsLib;
using VictorBush.Ego.NefsLib.Item;

namespace NefsEditCLI;

public class NefsUtils
{
    public static IEnumerable<KeyValuePair<string, NefsItem>> DeepEnumerateWithFullPath(NefsArchive archive)
    {
        return archive.Items.EnumerateRootItems()
            .SelectMany(i => DeepEnumerateItemOrChildren(archive, "", i));
    }

    private static IEnumerable<KeyValuePair<string, NefsItem>> DeepEnumerateItemOrChildren(
        NefsArchive archive,
        string pathToParent,
        NefsItem item
    )
    {
        var pathToItem = pathToParent + "/" + item.FileName;
        yield return KeyValuePair.Create(pathToItem, item);
        
        if (item.Attributes.IsDirectory)
        {
            foreach (var subItem in DeepEnumerateDirectory(archive, pathToItem, item.Id))
            {
                yield return subItem;
            }
        }
    }

    public static IEnumerable<KeyValuePair<string, NefsItem>> DeepEnumerateDirectory(
        NefsArchive archive,
        string pathToDirectory,
        NefsItemId directoryId
    )
    {
        return archive.Items.EnumerateItemChildren(directoryId)
            .SelectMany(child => DeepEnumerateItemOrChildren(archive, pathToDirectory, child));
    }
    
    public static string GetPathToItem(NefsArchive archive, NefsItemId itemId)
    {
        NefsItem? item = archive.Items.GetItem(itemId);
        string path = "";
        do
        {
            path = "/" + item.FileName;
            item = archive.Items.GetItemParent(item.Id);
        } while (item != null);

        return path;
    }
}
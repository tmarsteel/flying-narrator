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
        if (item.Attributes.IsDirectory)
        {
            foreach (var subItem in DeepEnumerateDirectory(archive, pathToItem, item.Id))
            {
                yield return subItem;
            }
        }
        else
        {
            yield return KeyValuePair.Create<string, NefsItem>(pathToItem, item);
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
}
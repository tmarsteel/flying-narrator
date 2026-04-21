using System.CommandLine.Parsing;

namespace NefsEditCLI;

public class IsExistingDirectoryValidator
{
    public static Action<OptionResult> make()
    {
        return result =>
        {
            var finfo = result.GetValueOrDefault<FileInfo>();
            var isDir = finfo.Attributes.HasFlag(FileAttributes.Directory);
            var exists = finfo.Exists;
            if (!isDir)
            {
                result.AddError(result.Option.Name + "=" + finfo.FullName + " must be a directory");
            }
        };
    }
}
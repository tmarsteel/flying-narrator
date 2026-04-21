using System.CommandLine.Parsing;

namespace NefsEditCLI;

public class IsExistingFileValidator
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
                if (!exists)
                {
                    result.AddError("File " + result.Option.Name + "=" + finfo.FullName + " does not exist");                   
                }
            }
            else
            {
                result.AddError(result.Option.Name + "=" + finfo.FullName + " must be a file");
            }
        };
    }
}
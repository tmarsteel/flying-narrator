using System.Text.Json.Serialization;

namespace NefsEditCLI.Interface;

[JsonPolymorphic(TypeDiscriminatorPropertyName = "op")]
[JsonDerivedType(typeof(Enumerate), typeDiscriminator: "ls")]
[JsonDerivedType(typeof(Extract), typeDiscriminator: "extract")]
[JsonDerivedType(typeof(Exit), typeDiscriminator: "exit")]
public abstract class NefsCommand
{
    public class Enumerate : NefsCommand
    {
        public class Result(List<string> files)
        {
            public List<string> files { get; set; } = files;
        }
    }

    public class Extract : NefsCommand
    {
        public string pathInNefs { get; set; }
        public string extractTo { get; set; }
        public bool decodeBinaryXml { get; set; } = false;
        
        public class Result(bool itemLocated, bool extractionSuccessful)
        {
            public bool itemLocated { get; set; } = itemLocated;
            public bool extractionSuccessful { get; set; } = extractionSuccessful;
        }
    }

    public class Exit : NefsCommand
    {
        
    }
}
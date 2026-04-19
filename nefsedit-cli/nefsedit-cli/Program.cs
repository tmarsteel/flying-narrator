using EgoEngineLibrary.Language;
using VictorBush.Ego.NefsLib;

namespace nefsedit_cli
{
    internal class Program
    {
        static void Main(string[] args)
        {
            Console.WriteLine("Hello, World!");
            LngFile f = new LngFile(new FileStream(args[0], FileMode.Open));
            Console.WriteLine(f.GetDataTable().Rows[0][0]);
            Console.WriteLine(NefsVersionExtensions.ToPrettyString(NefsVersion.Version130));
        }
    }
}

#include "pch.h"
#include "MotionController.h"

int main()
{
    if (!AfxWinInit(::GetModuleHandle(NULL), NULL, ::GetCommandLine(), 0))
    {
        std::cerr << "Fatal Error: MFC initialization failed" << std::endl;
        return 1;
    }

    MotionController controller;

    if (controller.Init())
    {
        controller.Run();
    }
    else
    {
        // [严格按照要求的错误提示]
        controller.Log("FATAL: Motion card initialization failed. Shutting down.");
        std::cerr << "Controller initialization failed. Exiting." << std::endl;
        std::cin.get();
    }

    return 0;
}
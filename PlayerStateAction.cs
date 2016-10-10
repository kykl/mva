﻿// Classes and structures being serialized

// Generated by ProtocolBuffer
// - a pure c# code generation implementation of protocol buffers
// Report bugs to: https://silentorbit.com/protobuf/

// DO NOT EDIT
// This file will be overwritten when CodeGenerator is run.
// To make custom modifications, edit the .proto file and add //:external before the message line
// then write the code and the changes in a separate file.
using System;
using System.Collections.Generic;

namespace Io.Bigfast
{
    public partial class PlayerStateAction
    {
        public string ChannelId { get; set; }

        public string UserId { get; set; }

        public string PlayId { get; set; }

        public ulong Timestamp { get; set; }

        public Io.Bigfast.PlayerStateAction.GameState PlayerState { get; set; }

        public List<Io.Bigfast.PlayerStateAction.GameState> TeamState { get; set; }

        public List<Io.Bigfast.PlayerStateAction.GameState> OpponentState { get; set; }

        public Io.Bigfast.PlayerStateAction.Action PlayerAction { get; set; }

        public partial class Position
        {
            public float X { get; set; }

            public float Y { get; set; }

            public float Z { get; set; }

        }

        public partial class Velocity
        {
            public float X { get; set; }

            public float Y { get; set; }

            public float Z { get; set; }

        }

        public partial class GameState
        {
            public Io.Bigfast.PlayerStateAction.Position Position { get; set; }

            public Io.Bigfast.PlayerStateAction.Velocity Velocity { get; set; }

        }

        public partial class Action
        {
            public bool Up { get; set; }

            public bool Down { get; set; }

            public bool Left { get; set; }

            public bool Right { get; set; }

            public bool Gas { get; set; }

            public bool Brake { get; set; }

            public bool Jump { get; set; }

            public bool Boost { get; set; }

        }

    }

}
